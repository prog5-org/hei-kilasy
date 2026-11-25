# Journal IA — Bibliothèque

Outil utilisé : [opencode], dans IntelliJ IDEA

## Changement : Pénalité de retard sur les notes (C1)

### échange 1 : 21:50:45

 Demande : " je reprends la maintenance d'un projet nommée kilasy je dois implémenter une fonctionalité l'implementation consiste a concevoir  une pénalité de retard sur les notes −10 % par                                           
┃  jour de retard entamé, plafond −50 %, note jamais négative exemple ici on a Alice dans l'exrcice tri à bulles deadline 2026-11-10 date et heure de soumissions 2026-11-09T18:00:00                                             
┃  ducoup grade 16 la note max parce que Alice est à l'heure si elle est en retard 2heures après l'échéance jours entamés = 1 donc sa note max le 16 donnée par le prof par rapport à                                     
┃  ses resultats sera -10% chaque jour entamés la note attribué sera de -10% pour 3 jours se sera 11.2 ainsi de suite ducoup pour cette grande fonctionalité analyse tous on ne                                           
┃  touche pas aux codes existant juste l'améliorer selon les besoins des clients donc c'est notre premier fonctionalité avant de tous implementer detail moi sans entrer trop en                                           
┃  technique ce que tu vas faire les fichier que tu vas modifier pourquoi et tu attend ma confirmation pour l'implementaiton"
  
  Réponse de l'agent : « J'ai analysé le projet. Voici mon plan, sans rien modifier pour l'instant.

Principe retenu : la note du professeur (ex. 16/20) reste stockée telle quelle. La pénalité sera calculée au moment de l'affichage : jours de retard entamés (par date calendaire) × 10 %, limités à 50 %, jamais en dessous de 0. Exemple sur « Tri à bulles » : 09/11 18h → 16,0 ; 11/11 02h → 14,4 ; 13/11 → 11,2 ; 18/11 → 8,0 (plafond).

Fichiers concernés :

| Fichier | Pourquoi |
|---|---|
| src/main/java/hei/school/GradeCalculator.java (nouveau) | Regrouper la règle en un seul endroit pour que l'API, l'e-mail, le relevé et le CSV affichent exactement la même note. |
| SubmissionController.java (get et grade) | C'est ici que la note est calculée aujourd'hui, envoyée par e-mail et renvoyée au professeur. |
| GradeReport.java | Le relevé et la moyenne doivent utiliser la note pénalisée, sinon ils contrediraient l'e-mail. |
| CsvExport.java | L'export tronque actuellement les notes (13,7 au lieu de 13,8) : je l'aligne sur le même calcul. |
| README.md | Documenter la nouvelle règle métier. |

Non modifiés : base de données, données de démonstration, exercices, étudiants. Les notes déjà saisies seront pénalisées automatiquement, sans migration, et un nouveau correctif ne pénalisera pas deux fois.

Vérification : les données de test contiennent déjà les 4 cas de ton exemple (Alice, Bob, Clara, David) ; je pourrai les contrôler via les endpoints et Mailpit. Si tu veux des tests automatisés, il faudra aussi toucher au pom.xml (dis-moi).

Points à confirmer : (1) comptage par jour calendaire, car l'échéance n'a pas d'heure stockée ; (2) pénalité appliquée à la note sur 20, pas aux points bruts du professeur ; (3) tests automatisés oui/non.

Je n'ai rien changé. Confirme-moi et je passe à l'implémentation. »

### échange 2 : 22:20:47

 Demande : " Comptage par jour calendaire : Oui, c'est validé. On se base sur la date de soumission par rapport à la date de la deadline (sans tenir compte de l'heure de la deadline, puisqu'elle n'est pas stockée). Pénalité sur la note sur 20 : Oui, on applique la pénalité sur la note sur 20 (celle calculée à partir des points), pas sur les points bruts. Tests automatisés : Non, pas besoin de toucher au pom.xml pour l'instant. On fera les vérifications manuellement via curl et Mailpit. Ton plan est validé. Tu peux passer à l'implémentation. Attention sur un point : tu as mentionné que CsvExport.java tronque actuellement les notes (13,7 au lieu de 13,8). Assure-toi que le nouveau GradeCalculator corrige bien ce problème d'arrondi pour que le CSV, le relevé et le grade soient strictement identiques (ex: 14.4 et pas 14.399999). Utilise BigDecimal si nécessaire. vas y "

  Réponse de l'agent : « Implémentation terminée. Nouveau fichier GradeCalculator.java : calcul unique en BigDecimal, jours calendaires entamés, -10 % par jour plafonnés à 50 %, plancher à 0, pénalité appliquée avant un arrondi HALF_UP à une décimale. SubmissionController.java (consultation et notation), GradeReport.java et CsvExport.java utilisent tous ce même calcul : plus de troncature, plus d'artefacts flottants. README.md documente la règle. Aucune modification de la base, des données de démo ni du pom.xml.

Vérifications : mvn compile OK. Via curl sur une instance temporaire port 8081 : soumissions 1 à 4 = 16.0 / 14.4 / 11.2 / 8.0 (plafond), Emma = 13.8 ; relevé et CSV identiques (14,4 / 11,2 / 8,0 / 13,8). Notation PUT sur la soumission 7 : 15.3/20 renvoyé, GET identique et e-mail Mailpit conforme ; donnée de démo et e-mail de test supprimés ensuite. Instance temporaire arrêtée, application du port 8080 intacte. »


Demande : "Comptage par jour calendaire : oui c'est validé  
on se base sur la date de soumission par rapport a la date de la deadline (sans tenir compte de l'heure de la deadline puisqu'elle n'est pas stockee)
Penaalitée sur la note sur 20 : oui on applique la pénalité sur la note sur 20 (celle calculée à partir des points) pas sur les points bruts
Tests automatisés : non pas besoin de toucher au pom.xml pour l'instant je ferais les vérifications manuellement via curl et Mailpit
Ton plan est validé. Tu peux passer à l'implémentation."

### échange 3 : 21:57:04

 Demande : "est ce qu'il y a des pièges dans se projet des incoherences "

  Réponse de l'agent : «

Oui, le projet présente plusieurs pièges et incohérences.

| Piège / incohérence | Justification |
|---|---|
| Calcul de la note répété dans 4 entités (détail, correction, relevé, CSV) avec 3 arrondis différents (Math.round, BigDecimal HALF_UP, Math.floor) | Le même devoir pouvait afficher 13,7 dans le CSV et 13,8 dans l'API, ou 14.399999 à cause des doubles. C'est pour cela que, dès le premier prompt, j'ai proposé de centraliser la règle dans GradeCalculator ; solution acceptée. |
| Deadline enregistrée comme DATE, sans heure | Le retard est compté en jours calendaires : un rendu à 00:01 le lendemain compte 1 jour entamé. Ce n'est pas un bug, c'est la règle que tu as validée. |
| Fuseaux horaires : LocalDateTime.now() côté Java, now() côté PostgreSQL, colonnes TIMESTAMP sans fuseau | Si la JVM et la base n'ont pas le même fuseau, le comptage peut se décaler d'un jour. Aucun fuseau n'est configuré dans le projet ; non corrigé car hors périmètre. |
| max_points = 0 présent en base | La validation n'existe qu'à la création d'exercice ; une valeur 0 provoquerait une division par zéro. GradeCalculator renvoie 0 par sécurité. |
| Schéma idempotent (CREATE TABLE IF NOT EXISTS) | Modifier la définition d'une table existante ne la met pas à jour : il faudrait un ALTER TABLE. C'est une raison de plus de ne pas toucher à la base. |
| Booléen late calculé à plusieurs endroits (Java et SQL) | Cohérent aujourd'hui avec daysLate > 0, mais dupliqué ; laissé tel quel pour ne pas modifier de code inutile. |

»




