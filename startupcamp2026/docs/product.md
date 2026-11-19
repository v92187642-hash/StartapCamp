# Product

What is being built, for whom, and the limits that shape the code. The full
argument with sources is in the pitch deck.

## Source material

- Pitch deck (14 slides, Russian, with sources):
  https://claude.ai/artifact/C2vVs6QByTfJvxFLAxy1Xv (private to the project owner)
- Topic catalogue: `Темы_ТИСБИ (1).pdf` in the repository root, topic 04,
  «Дневник выполнения назначений врача»
- Plan used to prepare the pitch: `plan.txt` in the repository root

## One sentence

A shared journal of a doctor's prescriptions for the patient, a relative and
the doctor.

## Users

| Role | Who | What they do in the product |
|---|---|---|
| Patient | Person with a plan already issued by a doctor, often 45+ | Sees the day, marks entries |
| Relative | Adult child, 30–55, living separately; the likely payer | Sees the same day remotely, gets alerts |
| Doctor | Not a user of the app | Receives a summary and a list of questions at the visit |

These segments are hypotheses. Interviews are postponed (see
`docs/decisions.md`).

## Core behaviour

- A plan has items with generic labels and times.
- Each entry is in one of three states: planned, marked, no mark.
- Marking takes one tap and records the time. A mark can be corrected.
- A relative with access sees the same journal.
- A missed entry can become a question for the doctor.

## Hard limits

These come from the topic catalogue and from Russian law. They are rules for
the code, not preferences.

| Limit | Reason |
|---|---|
| No drug names, dosages or diagnoses stored | 152-ФЗ treats health data as a special category; the catalogue requires fictional plans without drug names |
| No medical advice, including on a missed entry | Software that interprets medical data or calculates doses is a medical device and needs registration |
| A consent record before any data is stored | 152-ФЗ |
| Production data hosted in Russia | 152-ФЗ data localisation |
| No advertising of prescription drugs | Law on advertising 38-ФЗ, article 24 |
| Any advertising is labelled (erid) | 347-ФЗ; ad revenue also carries a 3% levy under 479-ФЗ |
| Pharmacy links: booking and non-prescription only | Online sale of prescription drugs barely works in Russia |
| No claims of a health effect | Catalogue rule; nothing has been measured |

A lawyer has not reviewed any of this. Review is planned for M5.

## Revenue models, in priority order

1. Premium subscription for the relative: sharing, doctor report, several plans
2. Referral links to pharmacies
3. Licences for clinics and insurers
4. Patient-support programmes for pharmaceutical companies
5. Ad banner in the free tier (lowest priority)

Only the first affects the code before M4: accounts need a premium flag.

## Russian market facts that affect engineering

- RuStore is the main distribution channel for Android; it has its own payment
  and push SDKs.
- Many phones have no Google services, so Firebase alone is not enough for push.
- Xiaomi, Huawei and Samsung battery optimisation kills background
  notifications; the app must guide the user through the settings.
- Paying for subscriptions through foreign app stores is unreliable from Russia.
