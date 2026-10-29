import { t, useLocale, getLocale } from '../i18n';
import { useRef } from 'react';

export default function SignaturePad({ onClear, canvasRef, disabled }) {
  useLocale();
  const drawing = useRef(false);
  const point = (event) => {
    const canvas = canvasRef.current;
    const bounds = canvas.getBoundingClientRect();
    return {
      x: (event.clientX - bounds.left) * (canvas.width / bounds.width),
      y: (event.clientY - bounds.top) * (canvas.height / bounds.height),
    };
  };
  const start = (event) => {
    if (disabled) return;
    event.preventDefault();
    const canvas = canvasRef.current;
    canvas.setPointerCapture(event.pointerId);
    const ctx = canvas.getContext('2d');
    const p = point(event);
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.strokeStyle = '#102d24';
    drawing.current = true;
  };
  const move = (event) => {
    if (!drawing.current || disabled) return;
    event.preventDefault();
    const p = point(event);
    const ctx = canvasRef.current.getContext('2d');
    ctx.lineTo(p.x, p.y);
    ctx.stroke();
  };
  const stop = () => { drawing.current = false; };

  return (
    <div className="signature-pad-wrap">
      <canvas ref={canvasRef} className="signature-pad" width="700" height="150"
        onPointerDown={start} onPointerMove={move} onPointerUp={stop} onPointerCancel={stop}
        aria-label={t("Dessinez ici la signature du candidat")} />
      <button type="button" className="tool-btn signature-clear" onClick={onClear} disabled={disabled}> {t("Effacer")} </button>
    </div>
  );
}
