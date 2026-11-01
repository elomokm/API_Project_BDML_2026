// Serveur de fichiers des deux clients (D-015) : sert clients/ sur le port 8081, sur toutes
// les interfaces réseau, pour que le téléphone puisse ouvrir les pages. Aucune dépendance,
// Node 22 ou plus. Il ne fait que livrer des fichiers : le code des clients s'exécute dans le
// navigateur et parle directement à l'API (port 8080).
//
// Lancement :  node scripts/serveur-clients.mjs

import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { networkInterfaces } from "node:os";
import { extname, join, normalize, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const PORT = Number(process.env.PORT_CLIENTS ?? 8081);
const RACINE = resolve(fileURLToPath(new URL("../clients", import.meta.url)));
const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".ico": "image/x-icon",
};

const ACCUEIL = `<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Messagerie</title>
<style>body{font-family:system-ui,sans-serif;max-width:28rem;margin:3rem auto;padding:0 1rem}
a{display:block;padding:1rem;margin:.75rem 0;border:1px solid #8884;border-radius:.75rem;
text-decoration:none;color:inherit}</style></head><body><h1>Messagerie</h1>
<a href="/mobile/">Client mobile</a><a href="/desktop/">Client bureau</a></body></html>`;

createServer(async (requete, reponse) => {
  try {
    const chemin = decodeURIComponent(new URL(requete.url, "http://x").pathname);
    if (chemin === "/") {
      return envoyer(reponse, 200, TYPES[".html"], ACCUEIL);
    }
    // Protection contre la sortie du dossier (« /../../etc/passwd ») : le chemin résolu doit
    // rester sous la racine des clients.
    let fichier = normalize(join(RACINE, chemin));
    if (!fichier.startsWith(RACINE + sep)) {
      return envoyer(reponse, 403, "text/plain", "Interdit");
    }
    if ((await stat(fichier)).isDirectory()) {
      if (!chemin.endsWith("/")) {
        reponse.writeHead(301, { Location: chemin + "/" });
        return reponse.end();
      }
      fichier = join(fichier, "index.html");
    }
    const contenu = await readFile(fichier);
    envoyer(reponse, 200, TYPES[extname(fichier)] ?? "application/octet-stream", contenu);
  } catch {
    envoyer(reponse, 404, "text/plain", "Introuvable");
  }
}).listen(PORT, "0.0.0.0", () => {
  console.log(`Clients servis depuis ${RACINE}`);
  console.log(`  Sur cet ordinateur : http://localhost:${PORT}/desktop/`);
  for (const adresse of adressesLocales()) {
    console.log(`  Sur le téléphone   : http://${adresse}:${PORT}/mobile/`);
  }
});

function envoyer(reponse, statut, type, corps) {
  // Pas de cache : une page modifiée est immédiatement prise en compte pendant la démo.
  reponse.writeHead(statut, { "Content-Type": type, "Cache-Control": "no-store" });
  reponse.end(corps);
}

function adressesLocales() {
  return Object.values(networkInterfaces())
    .flat()
    .filter((i) => i && i.family === "IPv4" && !i.internal)
    .map((i) => i.address);
}
