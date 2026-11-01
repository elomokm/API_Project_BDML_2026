// Client de test du canal WebSocket (étape 4) : joue le scénario de l'énoncé avec deux
// clients simultanés, contre le vrai serveur, en utilisant les mêmes API que les navigateurs
// (fetch et WebSocket). Aucune dépendance : Node 22 ou plus.
//
// Lancement (serveur démarré) :  node scripts/scenario-deux-clients.mjs [http://localhost:8080]

const BASE = process.argv[2] ?? "http://localhost:8080";
const WS = BASE.replace(/^http/, "ws") + "/ws/messages";
const suffixe = Date.now().toString(36);

const pause = (ms) => new Promise((resoudre) => setTimeout(resoudre, ms));

async function creerEtConnecter(nom) {
  const identifiants = { username: `${nom}_${suffixe}`, motDePasse: "motdepasse123" };
  const json = { "Content-Type": "application/json" };
  let reponse = await fetch(`${BASE}/api/comptes`, { method: "POST", headers: json, body: JSON.stringify(identifiants) });
  if (reponse.status !== 201) throw new Error(`Création de ${nom} : ${reponse.status}`);
  reponse = await fetch(`${BASE}/api/auth/login`, { method: "POST", headers: json, body: JSON.stringify(identifiants) });
  const { jeton, utilisateur } = await reponse.json();
  console.log(`[REST] ${utilisateur.username} (id ${utilisateur.id}) connecté, jeton reçu`);
  return { ...utilisateur, jeton };
}

// Ouvre le canal comme le fera le client JavaScript : le jeton voyage dans le sous-protocole
// (D-010), jamais dans l'URL. Chaque message reçu est affiché, et chaque message de la
// conversation reçu par son destinataire est aussitôt accusé (D-013).
function ouvrirCanal(utilisateur) {
  return new Promise((resoudre, rejeter) => {
    const canal = new WebSocket(WS, ["messagerie.v1", utilisateur.jeton]);
    canal.recus = [];
    canal.onopen = () => {
      console.log(`[WS] ${utilisateur.username} : canal ouvert (sous-protocole ${canal.protocol})`);
      resoudre(canal);
    };
    canal.onerror = () => rejeter(new Error(`Canal refusé pour ${utilisateur.username}`));
    canal.onmessage = (evenement) => {
      const recu = JSON.parse(evenement.data);
      canal.recus.push(recu);
      console.log(`[WS] ${utilisateur.username} reçoit ${JSON.stringify(recu)}`);
      if (recu.type === "message" && recu.destinataireId === utilisateur.id) {
        canal.send(JSON.stringify({ type: "accuse", messageId: recu.id }));
      }
    };
  });
}

async function etatsDesUtilisateurs(jeton) {
  const reponse = await fetch(`${BASE}/api/utilisateurs`, { headers: { Authorization: `Bearer ${jeton}` } });
  return Object.fromEntries((await reponse.json()).map((u) => [u.username, u.connecte]));
}

function verifier(condition, libelle) {
  console.log(`${condition ? "OK  " : "ÉCHEC"} ${libelle}`);
  if (!condition) process.exitCode = 1;
}

// Scénario

const alice = await creerEtConnecter("alice");
const bob = await creerEtConnecter("bob");

// 0. Canal refusé sans jeton valide
const refus = await new Promise((resoudre) => {
  const intrus = new WebSocket(WS, ["messagerie.v1", "faux.jeton.signature"]);
  intrus.onopen = () => { intrus.close(); resoudre(false); };
  intrus.onerror = () => resoudre(true);
});
verifier(refus, "un jeton invalide ne peut pas ouvrir le canal");

// 1 et 2. A puis B se connectent
const canalA = await ouvrirCanal(alice);
const canalB = await ouvrirCanal(bob);
await pause(300);
const etats = await etatsDesUtilisateurs(alice.jeton);
verifier(etats[alice.username] && etats[bob.username], "GET /api/utilisateurs : alice et bob connectés");

// 3. A envoie à B : apparition immédiate sur B
canalA.send(JSON.stringify({ type: "message", destinataireId: bob.id, contenu: "Bonjour Bob !" }));
await pause(500);
verifier(canalB.recus.some((m) => m.type === "message" && m.contenu === "Bonjour Bob !"), "B reçoit le message de A sans rien demander");

// 4. B envoie à A : apparition immédiate sur A
canalB.send(JSON.stringify({ type: "message", destinataireId: alice.id, contenu: "Salut Alice !" }));
await pause(500);
verifier(canalA.recus.some((m) => m.type === "message" && m.contenu === "Salut Alice !"), "A reçoit la réponse de B");

// Tentative d'usurpation : B se fait passer pour A
canalB.send(JSON.stringify({ type: "message", expediteurId: alice.id, destinataireId: alice.id, contenu: "Usurpation" }));
await pause(300);
verifier(canalB.recus.some((m) => m.type === "erreur" && m.code === "expediteur_invalide"), "usurpation d'expéditeur refusée");

// 5. A se déconnecte ; B lui écrit pendant son absence ; A revient
canalA.close();
await pause(300);
verifier((await etatsDesUtilisateurs(bob.jeton))[alice.username] === false, "A apparaît hors ligne");
canalB.send(JSON.stringify({ type: "message", destinataireId: alice.id, contenu: "Message pendant ton absence" }));
await pause(300);
const canalA2 = await ouvrirCanal(alice);
await pause(500);
verifier(canalA2.recus.some((m) => m.contenu === "Message pendant ton absence"), "le message reçu hors ligne est poussé à la reconnexion");

// Historique complet, toujours visible après reconnexion (REST)
const historique = await (await fetch(`${BASE}/api/messages/${bob.id}`, { headers: { Authorization: `Bearer ${alice.jeton}` } })).json();
console.log(`[REST] historique vu par alice : ${historique.map((m) => `${m.contenu} (${m.etat})`).join(" | ")}`);
verifier(historique.length === 3 && historique.every((m) => m.etat === "LIVRE"), "historique complet, chaque message livré");

canalA2.close();
canalB.close();
