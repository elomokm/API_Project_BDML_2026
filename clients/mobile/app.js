// Client mobile : un écran à la fois (connexion, contacts, conversation).
// REST pour le compte, les contacts et l'historique ; WebSocket pour le direct.
"use strict";

// L'API est sur le même hôte que la page, port 8080 : la page marche telle quelle en local
// et depuis le téléphone (http://<IP de l'ordinateur>:8081/mobile/).
const API = `${location.protocol}//${location.hostname}:8080`;
const CANAL = `${location.protocol === "https:" ? "wss" : "ws"}://${location.hostname}:8080/ws/messages`;

const etat = {
  jeton: null,
  moi: null,                  // { id, username }
  contacts: new Map(),        // id -> { id, username, connecte, nonLus }
  conversations: new Map(),   // id de l'interlocuteur -> messages triés par id
  idsRecus: new Set(),        // déduplication : un message peut arriver deux fois (D-013)
  ouverte: null,              // id de l'interlocuteur affiché
  canal: null,
  reconnexion: null,
  saisies: new Map(),         // id de qui m'écrit -> minuterie d'expiration de l'indicateur
  saisieEnvoyee: 0,           // dernier « je tape » envoyé (horodatage), 0 si je ne tape pas
  finSaisie: null,
};

// Indicateur de saisie (D-017) : renouvelé au plus toutes les 2,5 s pendant la frappe, fin
// signalée après 3 s sans frappe ; l'affichage reçu expire seul après 5 s.
const RENOUVELLEMENT_SAISIE_MS = 2500;
const FIN_SAISIE_MS = 3000;
const EXPIRATION_SAISIE_MS = 5000;

const $ = (id) => document.getElementById(id);

// REST

/** Appel REST en JSON. Le jeton est joint dans l'en-tête Authorization, jamais dans l'URL. */
async function api(chemin, { methode = "GET", corps } = {}) {
  const entetes = { "Content-Type": "application/json" };
  if (etat.jeton) entetes.Authorization = `Bearer ${etat.jeton}`;
  let reponse;
  try {
    reponse = await fetch(API + chemin, {
      method: methode,
      headers: entetes,
      body: corps === undefined ? undefined : JSON.stringify(corps),
    });
  } catch {
    // Aucune réponse : serveur arrêté, mauvaise adresse ou réseau coupé.
    throw new Error(`Serveur injoignable (${API}). Est-il lancé ?`);
  }
  if (reponse.status === 401 && etat.jeton) {
    // Jeton expiré ou refusé : retour à l'écran de connexion.
    deconnexion();
    throw new Error("Session expirée, reconnectez-vous.");
  }
  const contenu = await reponse.json().catch(() => null);
  if (!reponse.ok) {
    // Erreur au format ProblemDetail (D-006), avec les champs fautifs éventuels.
    const details = contenu?.erreurs?.map((e) => `${e.champ} ${e.message}`).join(", ");
    throw new Error(details || contenu?.detail || `Erreur ${reponse.status}`);
  }
  return contenu;
}

async function seConnecter(username, motDePasse) {
  const reponse = await api("/api/auth/login", { methode: "POST", corps: { username, motDePasse } });
  etat.jeton = reponse.jeton;
  etat.moi = reponse.utilisateur;
  // sessionStorage : survit au rafraîchissement, disparaît à la fermeture de l'onglet (D-014).
  sessionStorage.setItem("session", JSON.stringify({ jeton: etat.jeton, moi: etat.moi }));
  await demarrer();
}

async function chargerContacts() {
  const utilisateurs = await api("/api/utilisateurs");
  for (const u of utilisateurs) {
    if (u.id === etat.moi.id) continue;
    const existant = etat.contacts.get(u.id);
    etat.contacts.set(u.id, { ...u, nonLus: existant?.nonLus ?? 0 });
  }
  afficherContacts();
}

/** Historique en REST, paginé par curseur (D-006) : les 50 derniers, ou ceux avant un id. */
async function chargerHistorique(interlocuteur, avant) {
  const parametres = new URLSearchParams({ limite: "50" });
  if (avant) parametres.set("avant", avant);
  const messages = await api(`/api/messages/${interlocuteur}?${parametres}`);
  messages.forEach(memoriser);
  $("bouton-plus-anciens").hidden = messages.length < 50;
}

// WebSocket

function ouvrirCanal() {
  // Le jeton voyage dans le sous-protocole du handshake, jamais dans l'URL (D-010).
  const canal = new WebSocket(CANAL, ["messagerie.v1", etat.jeton]);
  etat.canal = canal;
  afficherEtatCanal("connexion…", false);

  canal.onopen = () => afficherEtatCanal("en ligne", true);
  canal.onmessage = (evenement) => recevoir(JSON.parse(evenement.data));
  canal.onclose = () => {
    if (etat.canal !== canal || !etat.jeton) return; // fermeture volontaire
    afficherEtatCanal("reconnexion…", false);
    // Reconnexion automatique ; si le jeton est expiré, le prochain appel REST renvoie 401
    // et ramène à l'écran de connexion.
    etat.reconnexion = setTimeout(async () => {
      try {
        await chargerContacts();
        ouvrirCanal();
      } catch { /* déjà traité par api() */ }
    }, 2000);
  };
}

/** Aiguillage des messages du serveur selon leur type (D-012). */
function recevoir(evenement) {
  switch (evenement.type) {
    case "message":
      recevoirMessage(evenement);
      break;
    case "presence": {
      const contact = etat.contacts.get(evenement.utilisateurId);
      if (contact) {
        contact.connecte = evenement.connecte;
        afficherContacts();
        afficherEnTete();
      } else {
        chargerContacts(); // nouvel utilisateur inscrit depuis le chargement
      }
      break;
    }
    case "etat":
      changerEtats(evenement.messageIds, evenement.etat);
      break;
    case "saisie":
      recevoirSaisie(evenement.expediteurId, evenement.actif);
      break;
    case "erreur":
      $("erreur-envoi").textContent = evenement.message;
      break;
  }
}

/** Mes messages passent à « livré » ou « lu » : mise à jour des coches (D-017). */
function changerEtats(ids, nouvelEtat) {
  const cibles = new Set(ids);
  for (const liste of etat.conversations.values()) {
    for (const message of liste) {
      if (cibles.has(message.id)) message.etat = nouvelEtat;
    }
  }
  if (etat.ouverte) afficherMessages(false);
}

/**
 * Signale la lecture (D-017) : conversation ouverte et page visible. Un seul événement
 * « lu jusqu'au message N » couvre tous les messages reçus non lus de la conversation.
 */
function signalerLecture() {
  if (!etat.ouverte || document.visibilityState !== "visible") return;
  if (etat.canal?.readyState !== WebSocket.OPEN) return;
  const nonLus = (etat.conversations.get(etat.ouverte) ?? [])
    .filter((m) => m.expediteurId === etat.ouverte && m.etat !== "LU");
  if (nonLus.length === 0) return;
  etat.canal.send(JSON.stringify({ type: "lu", messageId: nonLus.at(-1).id }));
  nonLus.forEach((m) => { m.etat = "LU"; });
}

function recevoirSaisie(expediteurId, actif) {
  clearTimeout(etat.saisies.get(expediteurId));
  if (actif) {
    // Si l'expéditeur ferme son application en pleine frappe, la fin n'arrive jamais :
    // l'indicateur disparaît seul.
    etat.saisies.set(expediteurId, setTimeout(() => recevoirSaisie(expediteurId, false), EXPIRATION_SAISIE_MS));
  } else {
    etat.saisies.delete(expediteurId);
  }
  afficherEnTete();
  afficherContacts();
}

/** Appelé à chaque frappe dans le champ de message. */
function jeTape() {
  if (!etat.ouverte || etat.canal?.readyState !== WebSocket.OPEN) return;
  if (Date.now() - etat.saisieEnvoyee > RENOUVELLEMENT_SAISIE_MS) {
    etat.canal.send(JSON.stringify({ type: "saisie", destinataireId: etat.ouverte, actif: true }));
    etat.saisieEnvoyee = Date.now();
  }
  clearTimeout(etat.finSaisie);
  etat.finSaisie = setTimeout(arreterSaisie, FIN_SAISIE_MS);
}

function arreterSaisie() {
  clearTimeout(etat.finSaisie);
  if (etat.saisieEnvoyee && etat.ouverte && etat.canal?.readyState === WebSocket.OPEN) {
    etat.canal.send(JSON.stringify({ type: "saisie", destinataireId: etat.ouverte, actif: false }));
  }
  etat.saisieEnvoyee = 0;
}

function recevoirMessage(message) {
  const pourMoi = message.destinataireId === etat.moi.id;
  // Accusé automatique (D-013) : le message est arrivé sur cet appareil.
  if (pourMoi) {
    etat.canal.send(JSON.stringify({ type: "accuse", messageId: message.id }));
  }
  const nouveau = memoriser(message);
  const interlocuteur = pourMoi ? message.expediteurId : message.destinataireId;
  if (pourMoi && etat.saisies.has(interlocuteur)) recevoirSaisie(interlocuteur, false);
  if (interlocuteur === etat.ouverte) {
    afficherMessages(true);
    signalerLecture();
  } else if (nouveau && pourMoi) {
    const contact = etat.contacts.get(interlocuteur);
    if (contact) contact.nonLus += 1;
    afficherContacts();
  }
}

function envoyer(contenu) {
  $("erreur-envoi").textContent = "";
  if (etat.canal?.readyState !== WebSocket.OPEN) {
    $("erreur-envoi").textContent = "Connexion au serveur en cours, réessayez.";
    return false;
  }
  // Ni expéditeur ni date : le serveur les fixe lui-même (D-012).
  etat.canal.send(JSON.stringify({ type: "message", destinataireId: etat.ouverte, contenu }));
  return true;
}

// État local

/** Range un message dans sa conversation ; renvoie false s'il était déjà connu (doublon). */
function memoriser(message) {
  if (etat.idsRecus.has(message.id)) return false;
  etat.idsRecus.add(message.id);
  const interlocuteur = message.expediteurId === etat.moi.id ? message.destinataireId : message.expediteurId;
  const liste = etat.conversations.get(interlocuteur) ?? [];
  liste.push(message);
  liste.sort((a, b) => a.id - b.id);
  etat.conversations.set(interlocuteur, liste);
  return true;
}

// Affichage (toujours textContent : un message n'est jamais interprété comme du HTML)

function montrer(ecran) {
  for (const id of ["ecran-connexion", "ecran-contacts", "ecran-conversation"]) {
    $(id).hidden = id !== ecran;
  }
}

function afficherEtatCanal(texte, ouvert) {
  $("etat-canal").textContent = texte;
  $("etat-canal").classList.toggle("ouvert", ouvert);
}

function afficherContacts() {
  const liste = $("liste-contacts");
  liste.replaceChildren();
  const contacts = [...etat.contacts.values()]
    .sort((a, b) => (b.connecte - a.connecte) || a.username.localeCompare(b.username));
  for (const contact of contacts) {
    const bouton = document.createElement("button");
    bouton.type = "button";
    bouton.addEventListener("click", () => ouvrirConversation(contact.id));

    const pastille = document.createElement("span");
    pastille.className = "pastille" + (contact.connecte ? " en-ligne" : "");
    const nom = document.createElement("span");
    nom.className = "nom";
    nom.textContent = contact.username;
    const presence = document.createElement("span");
    presence.className = "presence";
    presence.textContent = etat.saisies.has(contact.id) ? "écrit…" : contact.connecte ? "en ligne" : "hors ligne";
    bouton.append(pastille, nom, presence);

    if (contact.nonLus > 0) {
      const badge = document.createElement("span");
      badge.className = "non-lus";
      badge.textContent = contact.nonLus;
      bouton.append(badge);
    }
    const element = document.createElement("li");
    element.append(bouton);
    liste.append(element);
  }
  $("aucun-contact").hidden = contacts.length > 0;
}

function afficherEnTete() {
  const contact = etat.contacts.get(etat.ouverte);
  if (!contact) return;
  $("nom-interlocuteur").textContent = contact.username;
  $("statut-interlocuteur").textContent = contact.connecte ? "en ligne" : "hors ligne";
  $("statut-interlocuteur").classList.toggle("en-ligne", contact.connecte);
  $("saisie-interlocuteur").hidden = !etat.saisies.has(contact.id);
  $("saisie-interlocuteur").textContent = `${contact.username} est en train d'écrire…`;
}

function afficherMessages(defilerEnBas) {
  const fil = $("fil");
  const liste = $("liste-messages");
  liste.replaceChildren();
  for (const message of etat.conversations.get(etat.ouverte) ?? []) {
    const bulle = document.createElement("li");
    if (message.expediteurId === etat.moi.id) bulle.className = "moi";
    bulle.textContent = message.contenu;
    const heure = document.createElement("time");
    heure.dateTime = message.dateEnvoi;
    heure.textContent = formaterDate(message.dateEnvoi);
    if (message.expediteurId === etat.moi.id) heure.append(coches(message.etat));
    bulle.append(heure);
    liste.append(bulle);
  }
  if (defilerEnBas) fil.scrollTop = fil.scrollHeight;
}

/** Une coche : envoyé ; deux : livré ; deux colorées : lu (D-016). */
function coches(etatMessage) {
  const marque = document.createElement("span");
  marque.className = "coches" + (etatMessage === "LU" ? " lu" : "");
  marque.textContent = etatMessage === "ENVOYE" ? " ✓" : " ✓✓";
  marque.title = { ENVOYE: "Envoyé", LIVRE: "Livré", LU: "Lu" }[etatMessage];
  return marque;
}

/** « 2026-09-21T10:15:30 » devient « 10:15 », précédé du jour s'il ne s'agit pas d'aujourd'hui. */
function formaterDate(dateEnvoi) {
  const [jour, heure] = dateEnvoi.split("T");
  const aujourdHui = new Date().toLocaleDateString("sv"); // format AAAA-MM-JJ
  const [a, m, j] = jour.split("-");
  return (jour === aujourdHui ? "" : `${j}/${m}/${a} `) + heure.slice(0, 5);
}

// Navigation

async function ouvrirConversation(interlocuteur) {
  arreterSaisie();
  etat.ouverte = interlocuteur;
  etat.contacts.get(interlocuteur).nonLus = 0;
  $("erreur-envoi").textContent = "";
  afficherEnTete();
  montrer("ecran-conversation");
  afficherMessages(true);
  try {
    await chargerHistorique(interlocuteur);
    afficherMessages(true);
    signalerLecture();
  } catch (erreur) {
    $("erreur-envoi").textContent = erreur.message;
  }
}

async function demarrer() {
  $("mon-nom").textContent = etat.moi.username;
  montrer("ecran-contacts");
  await chargerContacts();
  ouvrirCanal();
}

function deconnexion() {
  clearTimeout(etat.reconnexion);
  clearTimeout(etat.finSaisie);
  etat.saisies.forEach(clearTimeout);
  const canal = etat.canal;
  Object.assign(etat, {
    jeton: null, moi: null, contacts: new Map(), conversations: new Map(),
    idsRecus: new Set(), ouverte: null, canal: null, saisies: new Map(), saisieEnvoyee: 0,
  });
  canal?.close();
  sessionStorage.removeItem("session");
  $("champ-mot-de-passe").value = "";
  montrer("ecran-connexion");
}

// Événements de l'interface

$("formulaire-connexion").addEventListener("submit", async (evenement) => {
  evenement.preventDefault();
  $("erreur-connexion").textContent = "";
  try {
    await seConnecter($("champ-username").value.trim(), $("champ-mot-de-passe").value);
  } catch (erreur) {
    $("erreur-connexion").textContent = erreur.message;
  }
});

$("bouton-inscription").addEventListener("click", async () => {
  $("erreur-connexion").textContent = "";
  const username = $("champ-username").value.trim();
  const motDePasse = $("champ-mot-de-passe").value;
  try {
    await api("/api/comptes", { methode: "POST", corps: { username, motDePasse } });
    await seConnecter(username, motDePasse);
  } catch (erreur) {
    $("erreur-connexion").textContent = erreur.message;
  }
});

$("bouton-deconnexion").addEventListener("click", deconnexion);

$("bouton-retour").addEventListener("click", () => {
  arreterSaisie();
  etat.ouverte = null;
  afficherContacts();
  montrer("ecran-contacts");
});

$("bouton-plus-anciens").addEventListener("click", async () => {
  const premier = etat.conversations.get(etat.ouverte)?.[0];
  if (!premier) return;
  const fil = $("fil");
  const hauteurAvant = fil.scrollHeight;
  await chargerHistorique(etat.ouverte, premier.id);
  afficherMessages(false);
  fil.scrollTop = fil.scrollHeight - hauteurAvant; // garde la position de lecture
});

$("formulaire-envoi").addEventListener("submit", (evenement) => {
  evenement.preventDefault();
  const champ = $("champ-message");
  const contenu = champ.value.trim();
  if (contenu && envoyer(contenu)) {
    champ.value = "";
    arreterSaisie();
  }
});

$("champ-message").addEventListener("input", jeTape);

// Revenir sur l'application, conversation ouverte : ce qui est arrivé entre-temps est lu.
document.addEventListener("visibilitychange", signalerLecture);

// Reprise d'une session existante (rafraîchissement de la page)

const session = JSON.parse(sessionStorage.getItem("session") ?? "null");
if (session) {
  etat.jeton = session.jeton;
  etat.moi = session.moi;
  demarrer().catch(() => deconnexion());
} else {
  montrer("ecran-connexion");
}
