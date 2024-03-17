# StopDemarchage

This application implements a simple CallScreeningService to filter:

- Incoming commercial calls
- Incoming unknown mobile phone numbers
- Outgoing commercial calls

## Inner workings

Call filtering is based on phone number prefixes according the rules defined by
 the ARCEP in ["Décision no 2022-1583 de l’Autorité de régulation des communications électroniques, des postes et de la distribution de la presse en date du 1er septembre 2022 modifiant la décision établissant le plan national de numérotation et ses règles de gestion"](https://www.arcep.fr/uploads/tx_gsavis/22-1583.pdf).

Commercial numbers:

- Metropolitan France: 0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948 et 0949.
- Guadeloupe: 09475
- Saint-Martin et à Saint-Barthélemy: 09476
- Guyane: 09477
- Martinique: 09478
- Réunion et Mayotte: 09479

Mobile numbers ("06" and "073 - 079") are only allows if they are in the contact list.

## Implementation status:

- TODO - Test CallerID authentification and add it

## License

This code is distributed under the GNU GPLv3 license, see LICENSE.md.