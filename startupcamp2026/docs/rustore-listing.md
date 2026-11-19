# RuStore listing

A draft, not submitted. Publishing needs things only the team can provide; the
checklist is at the end. Store texts are in Russian.

## Texts

**Name:** Отметка

**Short description (up to 80 characters):**
Дневник назначений врача: напоминания и отметки, которые видит близкий

**Full description:**

> «Отметка» — дневник выполнения назначений врача.
>
> Внесите план, который вам уже выдал врач, и отмечайте выполнение одним
> нажатием. Напоминание приходит в назначенное время, даже если приложение
> закрыто и нет интернета.
>
> — День на одном экране: что запланировано, что отмечено, где отметки нет.
> — Крупный текст и большие кнопки.
> — Отметки сохраняются без интернета и отправляются, когда связь появится.
> — Близкий человек видит ваш дневник по вашему приглашению и узнаёт, если
>   отметки нет.
> — Вопросы врачу: записывайте их по ходу лечения и берите список на приём.
> — Отчёт для врача: отметки по дням в одном файле.
>
> В приложении нет названий лекарств и диагнозов: назначения называются по
> номерам, как в листе врача.
>
> «Отметка» не является медицинским изделием и не даёт медицинских советов.
> По всем вопросам лечения обращайтесь к врачу.

**Category:** Здоровье (to be confirmed in the console)

**Age rating:** to be chosen in the console; the app has no restricted content.

## Wording rules

Any text in the listing must follow the limits in `docs/product.md`:

- no claim of a health effect ("улучшает", "помогает вылечить", "повышает
  приверженность"): nothing has been measured;
- no drug names;
- nothing that sounds like medical advice.

## Checklist before submitting

- [ ] Developer account in RuStore Console (a person or a company; the team decides)
- [ ] Signed release build: create a signing key, keep it safe, add a signing config
- [ ] Release build points at the production server (`-PapiBaseUrl=https://<domain>/api/`)
- [ ] App icon (the app has no icon of its own yet) and store graphics
- [ ] Screenshots from a real device: plans, day view, reminder, battery guide
- [ ] Privacy policy published at a public address (`docs/legal/privacy-policy-draft.md`, after the lawyer's review)
- [ ] RuStore Push project created; its id and token set on the server; the app registers its token (not built yet, see `docs/state.md`)
- [ ] The app run on real Xiaomi, Huawei and Samsung phones
