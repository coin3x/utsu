package com.utsusynth.utsu.model.song.pitch;

class Vibrato implements PitchMutation {
    private final double startMs; // Absolute start of vibrato in ms.
    private final double endMs; // Absolute end of vibrato in ms.
    private final double phaseIn; // Length in ms of phase in.
    private final double phaseOut; // Length in ms of phase out.
    private final double amplitude; // Max amplitude in tenths.
    private final double phase; // Radian value from 0 to 2π.
    private final double pitchChange; // In tenths.

    private final double baseFreq; // Frequency in radians/ms
    private final double startFreq; // Frequency at start of vibrato.
    private final double freqSlope; // Slope to apply to frequency, usually 0.

    Vibrato(
            double startMs,
            double endMs,
            int cycleMs,
            int amplitude,
            int phaseIn,
            int phaseOut,
            int phasePercent,
            int pitchChange,
            int baseFreqSlope) {
        this.startMs = startMs;
        this.endMs = endMs;
        double lengthMs = endMs - startMs;
        this.phaseIn = phaseIn / 100.0 * lengthMs;
        this.phaseOut = phaseIn / 100.0 * lengthMs;
        this.amplitude = amplitude / 10.0; // Convert cents into tenths.
        this.baseFreq = 2 * Math.PI / cycleMs;
        this.phase = 2 * Math.PI * (phasePercent / 100.0);
        this.pitchChange = pitchChange / 20.0; // Convert 2*cents into tenths.

        // Current min cycle length is .2 * base, current max is 1.8 * base.
        this.startFreq = baseFreq * (-1 * baseFreqSlope / 800.0 + 1);
        double endFreq = baseFreq * (baseFreqSlope / 800.0 + 1);
        this.freqSlope = lengthMs == 0 ? 0 : (endFreq - startFreq) / lengthMs;
    }

    @Override
    public double apply(int positionMs) {
        double frequency = startFreq + freqSlope * (positionMs - startMs);
        if (positionMs < startMs) {
            return 0;
        } else if (positionMs < startMs + phaseIn) {
            // Phase in.
            double incScale = Math.abs(positionMs - startMs) / phaseIn;
            return amplitude * incScale * Math.sin((positionMs - startMs) * frequency - phase)
                    + (pitchChange * incScale);
        } else if (positionMs < endMs - phaseOut) {
            // Main section of vibrato.
            return amplitude * Math.sin((positionMs - startMs) * frequency - phase) + pitchChange;
        } else if (positionMs < endMs) {
            // Phase out.
            double decScale = Math.abs(endMs - positionMs) / phaseOut;
            return amplitude * decScale * Math.sin((positionMs - startMs) * frequency - phase)
                    + (pitchChange * decScale);
        } else {
            return 0;
        }
    }
}
