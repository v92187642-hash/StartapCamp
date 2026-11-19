export type ItemDraft = { timeOfDay: string; daysOfWeek: number[] };

export const EVERY_DAY = [1, 2, 3, 4, 5, 6, 7];

const DAY_NAMES = ["Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"];

/** Time and weekdays of one plan item. The item's name is given by the server. */
export function ItemFields({ value, onChange }: { value: ItemDraft; onChange: (value: ItemDraft) => void }) {
  function toggle(day: number) {
    const days = value.daysOfWeek.includes(day)
      ? value.daysOfWeek.filter((d) => d !== day)
      : [...value.daysOfWeek, day].sort();
    onChange({ ...value, daysOfWeek: days });
  }

  return (
    <div className="item-fields">
      <label>
        Время
        <input
          type="time"
          required
          value={value.timeOfDay}
          onChange={(e) => onChange({ ...value, timeOfDay: e.target.value })}
        />
      </label>
      <fieldset>
        <legend>Дни недели</legend>
        {DAY_NAMES.map((name, index) => (
          <label key={name} className="day">
            <input
              type="checkbox"
              checked={value.daysOfWeek.includes(index + 1)}
              onChange={() => toggle(index + 1)}
            />
            {name}
          </label>
        ))}
      </fieldset>
    </div>
  );
}
