# Journal IA — Kilasy

Outil utilisé : opencode, dans IntelliJ IDEA

## Changement : Pénalité de retard sur les notes (C1)

### Échange 1 — Analyse et plan (21:50)

**Demande :** "Implémenter la pénalité de retard (-10%/jour, plafond -50%, note ≥ 0). Exemple avec Alice."

**Réponse de l'agent :** Proposition de centraliser le calcul dans une nouvelle classe `GradeCalculator`. Il a expliqué que l'ancienne version calculait la note à 4 endroits avec des arrondis différents (13,7 vs 13,8). Il propose de modifier `SubmissionController`, `GradeReport`, `CsvExport` et `README.md`.

**Décision :** Gardé. J'ai validé le plan en confirmant le comptage par jour calendaire, l'application de la pénalité sur la note sur 20, et le refus des tests automatisés (pour ne pas toucher au `pom.xml`).

### Échange 2 — Implémentation (22:20)

**Demande :** "Ton plan est validé. Passe à l'implémentation. Attention à bien utiliser BigDecimal pour l'arrondi."

**Réponse de l'agent :** Implémentation terminée. `GradeCalculator` créé. `SubmissionController`, `GradeReport`, `CsvExport` utilisent ce même calcul. Le bug d'arrondi du CSV est corrigé (13,8 au lieu de 13,7). Vérifications OK via curl.

**Décision :** Gardé. Vérifications manuelles avec `curl` : 16.0 / 14.4 / 11.2 / 8.0. Relevé et CSV identiques.

## Changement : SMS simulé à la publication d'une note (C2)

### Échange 1 — Analyse et plan (19:40)

**Demande :** "Quand un enseignant publie une note, l'étudiant doit recevoir, en plus de l'e-mail inchangé, un SMS simulé loggé au format `[SMS] +261 34 00 000 00 : Votre note pour « Tri à bulles » est disponible : 14,4/20.`. Contraintes : l'e-mail continue à fonctionner exactement comme avant ; si le SMS échoue, la note reste publiée (mise à jour en base et e-mail non annulés) ; changer de fournisseur plus tard ne doit pas obliger à modifier `SubmissionController` ; pas de réécriture, seuls les fichiers strictement nécessaires. Ne pas coder avant validation, mais consigner l'échange ici."

**Réponse de l'IA :** Le code de `grade()` a été relu sans modification : l'e-mail est construit aux lignes 144-154 et envoyé à la ligne 155 par `mailSender.send(message)`. La colonne `phone VARCHAR(20)` existe bien dans `student` (`schema.sql:15`) et est déjà sélectionnée par la requête de correction (`SubmissionController.java:117`).

**Architecture proposée (5 lignes) :**
1. Créer une interface `SmsSender` avec une seule méthode `void send(String phone, String message)`.
2. Créer `LogSmsSender implements SmsSender`, annotée `@Component`, qui affiche `[SMS] <phone> : <message>` avec `System.out.println`.
3. `SubmissionController` reçoit uniquement `SmsSender` par `@Autowired`, jamais l'implémentation concrète.
4. Changer de fournisseur revient à ajouter une autre implémentation et à sélectionner le bean (par exemple `@Primary`), sans toucher au contrôleur.
5. Le contrôleur dépend d'une abstraction : Spring choisit l'implémentation au démarrage, donc le code de notation reste stable.

**Fiabilité :** La mise à jour SQL et le bloc e-mail restent à leur place, inchangés ; l'appel SMS est ajouté juste après l'e-mail, dans un `try` avec `catch (Exception)` qui journalise l'échec. Aucune exception du SMS ne remonte : la note reste en base, l'e-mail est déjà parti et la réponse HTTP est renvoyée normalement. Aucun `@Transactional` n'est ajouté, donc rien ne peut annuler la publication.

**Format français :** `grade` est un `BigDecimal` déjà à une décimale ; le message utilise `String.format(Locale.FRANCE, "%.1f", grade)`, ce qui donne `14,4` avec une virgule, puis `Votre note pour « Tri à bulles » est disponible : 14,4/20.`.

**Fichiers concernés :** créer `src/main/java/hei/school/SmsSender.java` et `src/main/java/hei/school/LogSmsSender.java` ; modifier `src/main/java/hei/school/SubmissionController.java` (injection, numéro de téléphone, formatage, appel protégé) et `Journal_IA.md` (le présent échange). Aucune modification du schéma SQL, des données, du `pom.xml`, du `README.md` ni de `GradeCalculator`.

**Décision :** En attente de validation avant toute modification du code.

### Échange 2 — Implémentation (20:04)

**Demande :** "Plan validé. Passe à l'implémentation. Points d'attention : dans `LogSmsSender`, format exact du log `[SMS] <phone> : <message>` avec `System.out.println` ; dans `SubmissionController`, récupérer le phone depuis la ligne SQL existante (ligne 117) et l'utiliser dans `smsSender.send(phone, messageSms)` ; le `try/catch` doit englober uniquement l'appel SMS, pas l'email ni le `jdbcTemplate.update` ; ne toucher à rien d'autre, pas de `@Transactional`, pas de modif SQL, ni de `pom.xml`. Une fois fait, donne la liste exacte des fichiers, les commandes curl de test nominal et une méthode simple pour simuler une panne SMS puis la retirer. Mets aussi nos échanges dans le journal."

**Réponse de l'IA :** Fichiers créés : `src/main/java/hei/school/SmsSender.java` (interface avec `void send(String phone, String message)`) et `src/main/java/hei/school/LogSmsSender.java` (`@Component`, log exact `[SMS] +261 34 00 000 02 : Votre note pour « Pile et file » est disponible : 15,3/20.` via `System.out.println`). Fichiers modifiés : `src/main/java/hei/school/SubmissionController.java` (import `Locale`, injection de `SmsSender`, lecture du `phone` déjà présent dans le SELECT de la ligne 117, message français via `String.format(Locale.FRANCE, "%.1f", grade)`, appel SMS dans un `try/catch (Exception)` qui n'entoure que `smsSender.send(phone, messageSms)`) et `Journal_IA.md` (le présent échange). Rien d'autre n'a été touché : ni SQL, ni données, ni `pom.xml`, ni `@Transactional`.

Test nominal (mail + SMS) :

```bash
docker compose up -d
mvn spring-boot:run
curl -s -X PUT http://localhost:8080/submissions/7/grade \
  -H 'Content-Type: application/json' \
  -d '{"points":8.5,"feedback":"Test C2"}'
```

Attendu : HTTP 200, `grade = 15.3`, et dans les logs `Email de note envoyé à bob@etu.kilasy.test` puis `[SMS] +261 34 00 000 02 : Votre note pour « Pile et file » est disponible : 15,3/20.`.

Test de panne SMS : ajouter temporairement dans `LogSmsSender.send` un `if (phone.endsWith("000 06")) throw new RuntimeException("panne SMS simulée");`, recompiler/redémarrer, puis appeler la soumission 12 (téléphone se terminant par `000 06`) :

```bash
curl -s -X PUT http://localhost:8080/submissions/12/grade \
  -H 'Content-Type: application/json' \
  -d '{"points":20,"feedback":"Test panne SMS"}'
```

Attendu : HTTP 200, e-mail envoyé, log `Échec de l'envoi SMS à +261 34 00 000 06 : panne SMS simulée`, aucune ligne `[SMS]`, et note `10.0` bien enregistrée. La condition temporaire a ensuite été retirée et le projet recompilé.

**Décision :** Gardé. `mvn -o clean compile` OK ; cas nominal et cas de panne vérifiés sur une instance temporaire, conteneurs arrêtés et supprimés. La condition de panne n'est plus présente dans le code final.
