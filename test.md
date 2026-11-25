# Test manuel — pénalité de retard sur les notes

Aucun test automatique n'a été ajouté (choix validé) : `pom.xml` reste inchangé. Tout se vérifie à la main avec `curl` et Mailpit.

## 0. Préparation

```bash
docker compose up -d
mvn -q compile
mvn spring-boot:run
```

Important : si une application tournait déjà sur le port 8080, arrête-la et relance-la après les modifications, sinon tu testes l'ancien code.

- API : http://localhost:8080
- Mailpit : http://localhost:8025

Contrôle rapide que le nouveau code est bien chargé :

```bash
curl -s http://localhost:8080/submissions/2 | jq '{grade, late}'
```

Attendu : `"grade": 14.4` et `"late": true`. Si tu vois `16.0`, l'ancienne version tourne encore.

Si les données de démo ont été modifiées, repartir de zéro avec `docker compose down`, puis relancer les commandes ci-dessus.

## 1. Test direct de la fonction de calcul

```bash
jshell --class-path target/classes
```

Dans jshell, saisir :

```java
import hei.school.GradeCalculator;
import java.math.BigDecimal;
import java.time.*;

GradeCalculator.calculate(new BigDecimal("16"), new BigDecimal("20"), LocalDate.parse("2026-11-10"), LocalDateTime.parse("2026-11-09T18:00"));
GradeCalculator.calculate(new BigDecimal("16"), new BigDecimal("20"), LocalDate.parse("2026-11-10"), LocalDateTime.parse("2026-11-11T02:00"));
GradeCalculator.calculate(new BigDecimal("16"), new BigDecimal("20"), LocalDate.parse("2026-11-10"), LocalDateTime.parse("2026-11-13T10:00"));
GradeCalculator.calculate(new BigDecimal("16"), new BigDecimal("20"), LocalDate.parse("2026-11-10"), LocalDateTime.parse("2026-11-18T15:00"));
GradeCalculator.calculate(new BigDecimal("27.5"), new BigDecimal("40"), LocalDate.parse("2026-11-17"), LocalDateTime.parse("2026-11-17T18:30"));
```

Résultats attendus, dans l'ordre : `16.0`, `14.4`, `11.2`, `8.0` (plafond), `13.8`.

`GradeCalculator.daysLate(...)` et `GradeCalculator.penaltyRate(...)` peuvent être appelés de la même manière pour vérifier `1`/`0.10`, `3`/`0.30`, `5`/`0.50`.

## 2. Cas de l'énoncé via l'API

Exercice « Tri à bulles » : échéance 10/11, barème 20, 16 points attribués.

| Soumission | Étudiant | Date de rendu | Jours entamés | Note attendue |
|---|---|---|---|---|
| 1 | Alice | 09/11 18:00 | 0 | 16.0 |
| 2 | Bob | 11/11 02:00 | 1 | 14.4 |
| 3 | Clara | 13/11 10:00 | 3 | 11.2 |
| 4 | David | 18/11 15:00 | 5 (plafond) | 8.0 |
| 5 | Felix | 10/11 23:30 | 0 | pas de note (non corrigé) |

```bash
for id in 1 2 3 4 5; do
  printf 'sub %s : ' "$id"
  curl -s "http://localhost:8080/submissions/$id" | jq -c '{grade, late, rawPoints}'
done
```

Autres contrôles utiles :

- soumission 10 : 30/40, rendue le 19/11 pour une échéance au 17/11 → `12.0` ;
- soumission 11 : 27.5/40, rendue le 17/11 à 18:30 → `13.8` (l'ancien CSV affichait `13,7`).

## 3. Correction, e-mail et absence de double pénalité

La soumission 7 (Bob, « Pile et file », échéance 14/11, rendue le 15/11 08:40) n'est pas encore corrigée : 1 jour entamé.

```bash
curl -s -X PUT http://localhost:8080/submissions/7/grade \
  -H 'Content-Type: application/json' \
  -d '{"points":8.5,"feedback":"Test penalite"}' | jq -c '{grade, rawPoints, late}'

curl -s http://localhost:8080/submissions/7 | jq -c '{grade, late}'
```

Attendu dans les deux cas : `grade = 15.3` (8.5/10 = 17/20, moins 10 %).

Dans Mailpit (http://localhost:8025), le dernier e-mail envoyé à Bob doit contenir `15.3/20`. Équivalent en ligne de commande :

```bash
id=$(curl -s 'http://localhost:8025/api/v1/messages?limit=1' | jq -r '.messages[0].ID')
curl -s "http://localhost:8025/api/v1/message/$id" | jq -r .Text
```

Vérifier qu'une seconde correction ne pénalise pas deux fois :

```bash
curl -s -X PUT http://localhost:8080/submissions/2/grade \
  -H 'Content-Type: application/json' \
  -d '{"points":16,"feedback":"Verification"}' | jq -c '{grade, rawPoints}'
```

Attendu : `grade = 14.4` et `rawPoints = 16` (la note brute du professeur est conservée).

## 4. Relevé et export CSV

```bash
curl -s http://localhost:8080/students/3/transcript | jq -c '[.exercises[] | {exercise, grade, late}]'
curl -s http://localhost:8080/students/4/transcript | jq -c '[.exercises[] | {exercise, grade, late}]'
curl -s http://localhost:8080/students/5/transcript | jq -c '[.exercises[] | {exercise, grade, late}]'
curl -s http://localhost:8080/exports/grades.csv | tr -d '\r'
```

Attendu :

- Clara : « Tri à bulles » `11.2`, moyenne `12.60` ; CSV `11,2` ;
- David : « Tri à bulles » `8.0`, « Requêtes SQL » `12.0`, moyenne `10.00` ; CSV `8,0` et `12,0` ;
- Emma : « Requêtes SQL » `13.8` ; CSV `13,8`.

Le point virgule et la virgule décimale du CSV sont uniquement un format d'export pour Excel : la valeur est la même.

## 5. Cohérence des trois affichages pour une même soumission

Cas : Clara, soumission 3, 3 jours de retard.

```bash
curl -s http://localhost:8080/submissions/3 | jq -r .grade
curl -s http://localhost:8080/students/3/transcript | jq -r '.exercises[] | select(.exercise == "Tri à bulles") | .grade'
curl -s http://localhost:8080/exports/grades.csv | tr -d '\r' | awk -F';' '$2 == "Clara" && $5 == "Tri à bulles" {print $9}'
```

Attendu : `11.2`, `11.2`, `11,2`. Faire la même comparaison pour la soumission 4 (`8.0`, `8.0`, `8,0`), qui vérifie le plafond de −50 %.

## 6. Cas limites

- `10/11 23:30` (soumission 5) : rendu le jour de l'échéance, donc `late = false` et aucune pénalité.
- `points = 0` lors d'une correction : la note reste `0.0`, jamais négative.
- Une soumission non corrigée n'a pas de champ `grade`, que ce soit dans le détail, le relevé ou le CSV.

## 7. Nettoyage après les tests

Restaurer les données de démo modifiées par les corrections :

```bash
docker compose exec -T postgres psql -U kilasy -d kilasy -c "UPDATE submission SET raw_points = 16, feedback = 'Bon travail.', graded_at = '2026-11-20 09:05:00' WHERE id = 2;"
docker compose exec -T postgres psql -U kilasy -d kilasy -c "UPDATE submission SET raw_points = NULL, feedback = NULL, graded_at = NULL WHERE id = 7;"
```

Dans Mailpit, supprimer les e-mails de test via le bouton « Clear messages » de l'interface.
