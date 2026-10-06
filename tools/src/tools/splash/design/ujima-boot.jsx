// UjimaOS — Plymouth boot animation.
// Intro (Ignite → Build → Settle, plays once) → Loop (seamless throbber) → Outro (end animation).

const UJ_COLOR = { blue: '#5e81ac', cyan: '#88c0d0', gold: '#ebcb8b', hi: '#fff3dc' };

const UJ_MARK = {
  cx: 633, cy: 505, h: 614,
  blue: 'M508.5 748.4C491.9 746.6 477.8 742.4 461.5 734.4C452.2 729.8 450.6 728.6 437.5 715.5C398.4 676.4 373.1 626.2 363.3 568.0C361.0 554.2 360.0 238.4 362.3 234.0C365.3 228.0 365.8 227.9 418.0 228.3C463.2 228.5 465.8 228.7 471.7 230.7C484.4 234.9 493.8 243.0 499.3 254.4C505.1 266.2 505.0 262.5 505.0 397.3C505.0 478.0 505.4 524.5 506.1 530.8C512.4 588.1 550.4 631.0 605.2 642.6C615.4 644.7 637.2 645.0 648.0 643.1C672.2 638.8 694.5 627.4 712.3 610.1L721.2 601.5L718.5 607.5C717.1 610.8 712.9 618.7 709.3 625.0C673.9 687.2 615.0 733.1 554.6 745.5C542.7 748.0 518.6 749.5 508.5 748.4Z',
  cyan: 'M618.5 802.1C579.2 799.0 533.7 784.4 504.2 765.6C498.6 762.1 499.0 761.8 507.0 763.7C517.6 766.4 549.5 766.0 563.0 763.1C632.6 748.3 699.2 692.0 731.3 620.8C741.3 598.5 748.7 574.0 752.7 550.0C754.2 540.7 754.4 528.8 754.7 443.8C754.9 391.1 755.3 348.0 755.5 348.0C755.7 348.0 758.1 350.3 760.7 353.1C771.1 364.3 790.1 375.6 805.0 379.5C814.9 382.1 835.8 382.6 846.1 380.5C863.3 377.1 879.2 368.7 891.7 356.7L898.1 350.6L897.7 456.0C897.3 574.4 897.8 566.2 889.5 597.8C861.8 703.3 771.1 787.2 670.4 800.5C656.3 802.3 631.4 803.1 618.5 802.1Z',
  // draw-on guides: medial line of each stroke, from the top of its bar to its tip
  blueGuide: 'M433.5 200L433.5 545C433.5 625 480 672 560 684C620 688 690 650 724 598',
  cyanGuide: 'M826.5 330L826.5 545C826.5 620 760 712 700 748C650 772 560 782 500 764',
  ring: { cx: 827.9, cy: 284.9, r: 65.2, w: 24.4 },
};
const UJ_GUIDE_W = 180;

const UJ_MOTION = {
  enter: Easing.easeOutCubic,   // arrivals, settles
  draw: Easing.easeInOutCubic,  // draw-ons, sweeps, fades
  pop: Easing.easeOutBack,      // overshoot beats
};
const ujTw = (T, start, end, from, to, ease) => animate({ from, to, start, end, ease })(T);
const ujKf = (T, times, vals, ease) => interpolate(times, vals, ease)(T);

function ujAnnulus(cx, cy, R, r) {
  const c = (rad) => `M${cx + rad} ${cy}A${rad} ${rad} 0 1 0 ${cx - rad} ${cy}A${rad} ${rad} 0 1 0 ${cx + rad} ${cy}Z`;
  return c(R) + c(r);
}

// Arc on the ring, angles in degrees clockwise from 3 o'clock.
function UjArc({ start, len, color, opacity, width, filter }) {
  const { cx, cy, r, w } = UJ_MARK.ring;
  const L = clamp(len, 0, 360);
  if (L < 0.05 || !(opacity > 0.002)) return null;
  const C = 2 * Math.PI * r;
  return (
    <circle cx={cx} cy={cy} r={r} fill="none" stroke={color} strokeWidth={width || w}
      strokeDasharray={`${(L / 360) * C} ${C + 2}`} transform={`rotate(${start} ${cx} ${cy})`}
      opacity={opacity} filter={filter} />
  );
}

// Filled stroke revealed by a wide butt-capped brush running along its guide.
function UjStroke({ id, d, guide, fill, p, debug }) {
  if (debug) {
    return (
      <g>
        <path d={d} fill="#ff2d55" />
        <clipPath id={id}><path d={d} /></clipPath>
        <path d={guide} fill="none" stroke={fill} strokeWidth={UJ_GUIDE_W} clipPath={`url(#${id})`} />
        <path d={guide} fill="none" stroke="#ffffff" strokeWidth="3" />
      </g>
    );
  }
  if (!(p > 0)) return null;
  if (p >= 1) return <path d={d} fill={fill} />;
  return (
    <g>
      <clipPath id={id}><path d={d} /></clipPath>
      <path d={guide} fill="none" stroke={fill} strokeWidth={UJ_GUIDE_W} strokeLinecap="butt"
        pathLength="1" strokeDasharray="1 1" strokeDashoffset={1 - p} clipPath={`url(#${id})`} />
    </g>
  );
}

function UjGlow({ x, y, r, a }) {
  if (!(a > 0.002)) return null;
  return <circle cx={x} cy={y} r={r} fill="url(#ujGlow)" opacity={a} />;
}

function UjBootPiece({ size, loopStyle }) {
  const { T: Tn, CUES, time } = useComposition();
  const I = CUES.Ignite, B = CUES.Build, ST = CUES.Settle, LP = CUES.Loop, O = CUES.Outro;
  // Rest — the finished mark, still: Loop's first frame without its glint (added in the repo copy)
  const resting = Tn >= CUES.Rest;
  const T = resting ? LP : Tn;
  const P = Math.max(0.001, O - LP);
  const R = UJ_MARK.ring;
  const pulseStyle = loopStyle === 'Pulse';
  const debug = !!window.UJ_DEBUG;
  const TAU = Math.PI * 2;

  // Ignite — spark pops, squashes (anticipation), blooms into the ring with overshoot
  const pop = ujTw(T, I + 0.2, I + 0.5, 0, 1, UJ_MOTION.pop);
  const squash = ujTw(T, I + 0.5, I + 0.66, 0, 1, UJ_MOTION.enter);
  const bloom = ujTw(T, I + 0.66, I + 1.1, 0, 1, UJ_MOTION.pop);
  const disk = Math.max(0, 13 * pop - 3 * squash);
  const ringR = disk / 2 + (R.r - disk / 2) * bloom;
  const ringW = disk + (R.w - disk) * clamp(bloom, 0, 1);

  // Ring rides the build: dips as the cyan stroke pours out beneath it, lifts when the U closes
  const ringDy = ujKf(T, [B, B + 0.3, B + 0.85, B + 1.2, B + 1.6], [0, 5, 5, -8, 0],
    [UJ_MOTION.enter, UJ_MOTION.draw, UJ_MOTION.enter, UJ_MOTION.draw]);
  const ringPop = ujTw(T, O + 0.3, O + 0.62, 0, 0.06, UJ_MOTION.pop);

  // Build — the two strokes sweep down and around, passing each other at the base
  const pCyan = ujTw(T, B, B + 1.05, 0, 1, UJ_MOTION.draw);
  const pBlue = ujTw(T, B + 0.1, B + 1.15, 0, 1, UJ_MOTION.draw);

  // Settle — one breath, a sheen crosses bottom-left → top-right
  const breathScale = ujKf(T, [ST - 0.15, ST + 0.2, ST + 0.7], [1, 1.02, 1], [UJ_MOTION.enter, UJ_MOTION.draw]);
  const sheen = ujTw(T, ST - 0.05, ST + 0.7, -1, 1, UJ_MOTION.draw);

  // Loop — periodic in u, so T = Loop and T = Outro render the identical frame
  const u = (T - LP) / P;
  const head = -90 + 360 * (u - (0.55 / TAU) * Math.sin(TAU * u));
  const wave = (1 - Math.cos(TAU * u)) / 2;
  const birth = pulseStyle || resting ? 0 : ujTw(T, ST + 0.35, LP, 0, 1, UJ_MOTION.enter);
  const fillUp = pulseStyle ? 0 : ujTw(T, O, O + 0.5, 0, 1, UJ_MOTION.draw);
  const lenBase = (26 + 78 * wave) * birth;
  const glintLen = lenBase + (360 - lenBase) * fillUp;
  const tail = head - glintLen;
  const breath = pulseStyle && T >= LP && T <= O ? wave : 0;
  const lit = Math.min(1, ujTw(T, O + 0.25, O + 0.55, 0, 1, UJ_MOTION.draw) + 0.55 * breath);

  // Glows — ignition, outro flare, pulse breath
  const igA = ujKf(T, [I + 0.2, I + 0.45, I + 1.3], [0, 0.42, 0], [UJ_MOTION.enter, UJ_MOTION.draw]);
  const igR = ujTw(T, I + 0.2, I + 1.3, 40, 170, UJ_MOTION.enter);
  const flA = ujKf(T, [O + 0.3, O + 0.6, O + 1.15], [0, 0.42, 0], [UJ_MOTION.enter, UJ_MOTION.draw]);
  const flR = ujTw(T, O + 0.3, O + 1.15, 90, 210, UJ_MOTION.enter);

  // Outro — the mark lifts a touch and fades to the background
  const fade = ujTw(T, O + 0.55, O + 1.15, 1, 0, UJ_MOTION.draw);
  const lift = ujTw(T, O + 0.45, O + 1.15, 0, 0.035, UJ_MOTION.draw);

  const k = (size / UJ_MARK.h) * (breathScale + lift);
  const rcy = R.cy + ringDy;
  const ringTf = `translate(${R.cx} ${rcy}) scale(${1 + ringPop}) translate(${-R.cx} ${-R.cy})`;
  const sheenDir = (-35 * Math.PI) / 180;
  const sheenX = UJ_MARK.cx + Math.cos(sheenDir) * sheen * 520;
  const sheenY = UJ_MARK.cy + Math.sin(sheenDir) * sheen * 520;

  const comet = [];
  if (birth > 0) {
    for (let i = 0; i < 10; i++) {
      const st = tail + (glintLen * i) / 10;
      comet.push(<UjArc key={i} start={st} len={head - st} color={UJ_COLOR.hi} opacity={0.17 * birth} />);
    }
  }

  return (
    <div data-screen-label={`t=${Math.floor(time)}s`} style={{ position: 'absolute', inset: 0 }}>
      <svg width="1920" height="1080" viewBox="0 0 1920 1080" style={{ position: 'absolute', left: 0, top: 0, overflow: 'visible' }}>
        <defs>
          <linearGradient id="ujSheen" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stopColor="#ffffff" stopOpacity="0" />
            <stop offset="0.5" stopColor="#ffffff" stopOpacity="0.34" />
            <stop offset="1" stopColor="#ffffff" stopOpacity="0" />
          </linearGradient>
          <radialGradient id="ujGlow">
            <stop offset="0" stopColor={UJ_COLOR.gold} stopOpacity="0.85" />
            <stop offset="0.45" stopColor={UJ_COLOR.gold} stopOpacity="0.26" />
            <stop offset="1" stopColor={UJ_COLOR.gold} stopOpacity="0" />
          </radialGradient>
          <filter id="ujBlur" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="8" />
          </filter>
          <clipPath id="ujMarkClip">
            <path d={UJ_MARK.blue} />
            <path d={UJ_MARK.cyan} />
            <path d={ujAnnulus(R.cx, rcy, R.r + R.w / 2, R.r - R.w / 2)} clipRule="evenodd" />
          </clipPath>
        </defs>

        <g opacity={fade} transform={`translate(960 540) scale(${k}) translate(${-UJ_MARK.cx} ${-UJ_MARK.cy})`}>
          <UjGlow x={R.cx} y={rcy} r={igR} a={igA} />
          <UjGlow x={R.cx} y={rcy} r={flR} a={flA} />
          <UjGlow x={R.cx} y={rcy} r={130} a={0.22 * breath} />

          <UjStroke id="ujClipCyan" d={UJ_MARK.cyan} guide={UJ_MARK.cyanGuide} fill={UJ_COLOR.cyan} p={pCyan} debug={debug} />
          <UjStroke id="ujClipBlue" d={UJ_MARK.blue} guide={UJ_MARK.blueGuide} fill={UJ_COLOR.blue} p={pBlue} debug={debug} />

          <g transform={ringTf}>
            <UjArc start={tail} len={glintLen} color={UJ_COLOR.hi} opacity={0.5 * birth} width={R.w + 8} filter="url(#ujBlur)" />
            {ringW > 0.05 && (
              <circle cx={R.cx} cy={R.cy} r={ringR} fill="none" stroke={UJ_COLOR.gold} strokeWidth={ringW} />
            )}
            {comet}
            {lit > 0.002 && (
              <circle cx={R.cx} cy={R.cy} r={R.r} fill="none" stroke={UJ_COLOR.hi} strokeWidth={R.w} opacity={lit} />
            )}
          </g>

          {sheen > -1 && sheen < 1 && (
            <g clipPath="url(#ujMarkClip)">
              <rect x="-90" y="-700" width="180" height="1400" fill="url(#ujSheen)"
                transform={`translate(${sheenX} ${sheenY}) rotate(-35)`} />
            </g>
          )}
        </g>
      </svg>
    </div>
  );
}

const UJ_DEFAULTS = { motionEditor: true, bg: '#2E3440', logoSize: 320, loopStyle: 'Glint' };

function UjimaBoot() {
  const [t, setTweak] = useTweaks(Object.assign({}, UJ_DEFAULTS, window.TWEAK_DEFAULTS || {}));
  return (
    <React.Fragment>
      <CompositionStage width={1920} height={1080} scenes={window.OM_SCENES} playback={window.OM_PLAYBACK} bg={t.bg}>
        <UjBootPiece size={t.logoSize} loopStyle={t.loopStyle} />
      </CompositionStage>
      <TweaksPanel title="Tweaks">
        <TweakSection label="Boot screen" />
        <TweakColor label="Background" value={t.bg} options={['#2E3440', '#242933', '#000000']}
          onChange={(v) => setTweak('bg', v)} />
        <TweakSlider label="Logo height at 1080p" value={t.logoSize} min={160} max={520} step={8} unit="px"
          onChange={(v) => setTweak('logoSize', v)} />
        <TweakRadio label="Loop style" value={t.loopStyle} options={['Glint', 'Pulse']}
          onChange={(v) => setTweak('loopStyle', v)} />
        <TweakSection label="Editor" />
        <TweakToggle label="Motion editor" value={t.motionEditor} onChange={(v) => setTweak('motionEditor', v)} />
      </TweaksPanel>
    </React.Fragment>
  );
}

window.UjimaBoot = UjimaBoot;
