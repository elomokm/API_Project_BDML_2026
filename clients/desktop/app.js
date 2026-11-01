// Client bureau : deux panneaux (contacts et conversation), clavier et souris.
// REST pour le compte, les contacts et l'historique ; WebSocket pour le direct.
"use strict";

// L'API est sur le même hôte que la page, port 8080.
const API_URL = `${location.protocol}//${location.hostname}:8080`;
const CANAL_URL = `${location.protocol === "https:" ? "wss" : "ws"}://${location.hostname}:8080/ws/messages`;
const TAILLE_PAGE = 50;

const session = {
  jeton: null,
  utilisateur: null,      // { id, username }
  contacts: new Map(),    // id -> { id, username, connecte, nonLus }
  fils: new Map(),        // id de l'interlocuteur -> messages triés par id
  dejaVus: new Set(),     // déduplication par id (D-013)
  selection: null,        // id de l'interlocuteur affiché
  socket: null,
  minuterie: null,
};

const el = (id) => document.getElementById(id);

// Appels REST

/** Requête JSON vers l'API ; le jeton part dans l'en-tête Authorization (D-008). */
async function requete(chemin, methode = "GET", corps) {
  let reponse;
  try {
    reponse = await fetch(API_URL + chemin, {
      method: methode,
      headers: {
        "Content-Type": "application/json",
        ...(session.jeton ? { Authorization: `Bearer ${session.jeton}` } : {}),
      },
      body: corps === undefined ? undefined : JSON.stringify(corps),
    });
  } catch {
    // Aucune réponse : serveur arrêté, mauvaise adresse ou réseau coupé.
    throw new Error(`Serveur injoignable (${API_URL}). Est-il lancé ?`);
  }
  if (reponse.status === 401 && session.jeton) {
    fermerSession();
    throw new Error("Votre session a expiré, reconnectez-vous.");
  }
  const json = await reponse.json().catch(() => null);
  if (!reponse.ok) {
    // ProblemDetail (D-006) : détail général, ou liste des champs invalides.
    const champs = json?.erreurs?.map((e) => `${e.champ} ${e.message}`).join(" ; ");
    throw new Error(champs || json?.detail || `Erreur ${reponse.status}`);
  }
  return json;
}

async function connexion(username, motDePasse) {
  const { jeton, utilisateur } = await requete("/api/auth/login", "POST", { username, motDePasse });
  session.jeton = jeton;
  session.utilisateur = utilisateur;
  // sessionStorage : propre à l'onglet, effacé à sa fermeture (D-014).
  sessionStorage.setItem("messagerie", JSON.stringify({ jeton, utilisateur }));
  await ouvrirApplication();
}

async function rafraichirContacts() {
  for (const u of await requete("/api/utilisateurs")) {
    if (u.id === session.utilisateur.id) continue;
    session.contacts.set(u.id, { ...u, nonLus: session.contacts.get(u.id)?.nonLus ?? 0 });
  }
  dessinerContacts();
}

/** Historique paginé par curseur (D-006). */
async function chargerPage(interlocuteur, avant) {
  const query = new URLSearchParams({ limite: String(TAILLE_PAGE), ...(avant ? { avant } : {}) });
  const page = await requete(`/api/messages/${interlocuteur}?${query}`);
  page.forEach(ranger);
  el("bouton-plus-anciens").hidden = page.length < TAILLE_PAGE;
}

// Canal temps réel

function connecterSocket() {
  // Jeton transmis au handshake dans le sous-protocole, jamais dans l'URL (D-010).
  const socket = new WebSocket(CANAL_URL, ["messagerie.v1", session.jeton]);
  session.socket = socket;
  indiquerEtatCanal("connexion…", false);

  socket.addEventListener("open", () => indiquerEtatCanal("connecté", true));
  socket.addEventListener("message", (e) => traiter(JSON.parse(e.data)));
  socket.addEventListener("close", () => {
    if (session.socket !== socket || !session.jeton) return; // fermeture volontaire
    indiquerEtatCanal("reconnexion…", false);
    session.minuterie = setTimeout(async () => {
      try {
        await rafraichirContacts(); // 401 si le jeton a expiré : retour à la connexion
        connecterSocket();
      } catch { /* géré par requete() */ }
    }, 2000);
  });
}

/** Aiguillage selon le type (D-012). */
function traiter(evenement) {
  if (evenement.type === "message") {
    arriveeMessage(evenement);
  } else if (evenement.type === "presence") {
    const contact = session.contacts.get(evenement.utilisateurId);
    if (!contact) {
      rafraichirContacts(); // utilisateur inscrit depuis l'ouverture
      return;
    }
    contact.connecte = evenement.connecte;
    dessinerContacts();
    dessinerEntete();
  } else if (evenement.type === "erreur") {
    el("erreur-envoi").textContent = evenement.message;
  }
}

function arriveeMessage(message) {
  const recu = message.destinataireId === session.utilisateur.id;
  if (recu) {
    // Accusé automatique : le message est arrivé sur cet appareil (D-013).
    session.socket.send(JSON.stringify({ type: "accuse", messageId: message.id }));
  }
  const inedit = ranger(message);
  const interlocuteur = recu ? message.expediteurId : message.destinataireId;
  if (interlocuteur === session.selection) {
    dessinerMessages(true);
  } else if (inedit && recu) {
    const contact = session.contacts.get(interlocuteur);
    if (contact) contact.nonLus += 1;
    dessinerContacts();
  }
}

function envoyerMessage(contenu) {
  el("erreur-envoi").textContent = "";
  if (session.socket?.readyState !== WebSocket.OPEN) {
    el("erreur-envoi").textContent = "Le canal n'est pas ouvert, nouvelle tentative en cours.";
    return false;
  }
  // Le serveur fixe l'expéditeur et la date (D-012).
  session.socket.send(JSON.stringify({ type: "message", destinataireId: session.selection, contenu }));
  return true;
}

// Données locales

/** Ajoute un message à son fil ; false si déjà connu (doublon possible, D-013). */
function ranger(message) {
  if (session.dejaVus.has(message.id)) return false;
  session.dejaVus.add(message.id);
  const autre = message.expediteurId === session.utilisateur.id ? message.destinataireId : message.expediteurId;
  const fil = session.fils.get(autre) ?? [];
  fil.push(message);
  fil.sort((a, b) => a.id - b.id);
  session.fils.set(autre, fil);
  return true;
}

// Rendu (textContent uniquement : aucun contenu utilisateur n'est interprété comme HTML)

function indiquerEtatCanal(texte, ouvert) {
  el("etat-canal").textContent = texte;
  el("etat-canal").classList.toggle("ouvert", ouvert);
}

function dessinerContacts() {
  const liste = el("liste-contacts");
  liste.replaceChildren();
  const tries = [...session.contacts.values()]
    .sort((a, b) => (b.connecte - a.connecte) || a.username.localeCompare(b.username));
  for (const contact of tries) {
    const bouton = document.createElement("button");
    bouton.type = "button";
    bouton.title = contact.connecte ? "En ligne" : "Hors ligne";
    if (contact.id === session.selection) bouton.setAttribute("aria-current", "true");
    bouton.addEventListener("click", () => selectionner(contact.id));

    const pastille = document.createElement("span");
    pastille.className = "pastille" + (contact.connecte ? " en-ligne" : "");
    const nom = document.createElement("span");
    nom.className = "nom";
    nom.textContent = contact.username;
    bouton.append(pastille, nom);
    if (contact.nonLus > 0) {
      const badge = document.createElement("span");
      badge.className = "non-lus";
      badge.textContent = contact.nonLus;
      bouton.append(badge);
    }
    const ligne = document.createElement("li");
    ligne.append(bouton);
    liste.append(ligne);
  }
  el("aucun-contact").hidden = tries.length > 0;
}

function dessinerEntete() {
  const contact = session.contacts.get(session.selection);
  if (!contact) return;
  el("nom-interlocuteur").textContent = contact.username;
  el("statut-interlocuteur").textContent = contact.connecte ? "en ligne" : "hors ligne";
  el("statut-interlocuteur").classList.toggle("en-ligne", contact.connecte);
}

function dessinerMessages(versLeBas) {
  const zone = el("fil");
  const liste = el("liste-messages");
  liste.replaceChildren();
  for (const message of session.fils.get(session.selection) ?? []) {
    const ligne = document.createElement("li");
    if (message.expediteurId === session.utilisateur.id) ligne.className = "moi";
    ligne.textContent = message.contenu;
    const meta = document.createElement("time");
    meta.className = "meta";
    meta.dateTime = message.dateEnvoi;
    meta.textContent = horodatage(message.dateEnvoi);
    ligne.append(meta);
    liste.append(ligne);
  }
  if (versLeBas) zone.scrollTop = zone.scrollHeight;
}

/** « 2026-09-21T10:15:30 » : heure seule si aujourd'hui, sinon date et heure. */
function horodatage(dateEnvoi) {
  const [jour, heure] = dateEnvoi.split("T");
  const [annee, mois, j] = jour.split("-");
  const aujourdHui = new Date().toLocaleDateString("sv");
  return jour === aujourdHui ? heure.slice(0, 5) : `${j}/${mois}/${annee} ${heure.slice(0, 5)}`;
}

// Navigation

async function selectionner(interlocuteur) {
  session.selection = interlocuteur;
  session.contacts.get(interlocuteur).nonLus = 0;
  el("sans-conversation").hidden = true;
  el("conversation").hidden = false;
  el("erreur-envoi").textContent = "";
  dessinerContacts();
  dessinerEntete();
  dessinerMessages(true);
  try {
    await chargerPage(interlocuteur);
    dessinerMessages(true);
  } catch (erreur) {
    el("erreur-envoi").textContent = erreur.message;
  }
  el("champ-message").focus();
}

async function ouvrirApplication() {
  el("mon-nom").textContent = session.utilisateur.username;
  el("vue-connexion").hidden = true;
  el("vue-application").hidden = false;
  await rafraichirContacts();
  connecterSocket();
}

function fermerSession() {
  clearTimeout(session.minuterie);
  const socket = session.socket;
  Object.assign(session, {
    jeton: null, utilisateur: null, contacts: new Map(), fils: new Map(),
    dejaVus: new Set(), selection: null, socket: null,
  });
  socket?.close();
  sessionStorage.removeItem("messagerie");
  el("champ-mot-de-passe").value = "";
  el("conversation").hidden = true;
  el("sans-conversation").hidden = false;
  el("vue-application").hidden = true;
  el("vue-connexion").hidden = false;
}

// Interactions

el("formulaire-connexion").addEventListener("submit", async (e) => {
  e.preventDefault();
  el("erreur-connexion").textContent = "";
  try {
    await connexion(el("champ-username").value.trim(), el("champ-mot-de-passe").value);
  } catch (erreur) {
    el("erreur-connexion").textContent = erreur.message;
  }
});

el("bouton-inscription").addEventListener("click", async () => {
  el("erreur-connexion").textContent = "";
  const username = el("champ-username").value.trim();
  const motDePasse = el("champ-mot-de-passe").value;
  try {
    await requete("/api/comptes", "POST", { username, motDePasse });
    await connexion(username, motDePasse);
  } catch (erreur) {
    el("erreur-connexion").textContent = erreur.message;
  }
});

el("bouton-deconnexion").addEventListener("click", fermerSession);

el("bouton-plus-anciens").addEventListener("click", async () => {
  const plusAncien = session.fils.get(session.selection)?.[0];
  if (!plusAncien) return;
  const zone = el("fil");
  const hauteur = zone.scrollHeight;
  await chargerPage(session.selection, plusAncien.id);
  dessinerMessages(false);
  zone.scrollTop = zone.scrollHeight - hauteur;
});

el("formulaire-envoi").addEventListener("submit", (e) => {
  e.preventDefault(); // Entrée dans le champ soumet le formulaire
  const champ = el("champ-message");
  const contenu = champ.value.trim();
  if (contenu && envoyerMessage(contenu)) champ.value = "";
});

// Reprise après rafraîchissement

const sauvegarde = JSON.parse(sessionStorage.getItem("messagerie") ?? "null");
if (sauvegarde) {
  session.jeton = sauvegarde.jeton;
  session.utilisateur = sauvegarde.utilisateur;
  ouvrirApplication().catch(fermerSession);
}
