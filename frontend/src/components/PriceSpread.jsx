/** Where this offer sits between the cheapest and dearest available offer. Purely visual (numbers are in the row). */
export default function PriceSpread({ position, best }) {
  if (position === null || position === undefined) return <span className="spread spread--off" aria-hidden />;
  return (
    <span className="spread" aria-hidden>
      <span className={`spread__dot${best ? ' spread__dot--best' : ''}`} style={{ left: `${position * 100}%` }} />
    </span>
  );
}
