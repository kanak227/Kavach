import math
import struct
import wave
import os

SAMPLE_RATE = 44100
base_dir = os.path.abspath(os.path.dirname(__file__))
raw_dir = os.path.join(base_dir, "app", "src", "main", "res", "raw")
os.makedirs(raw_dir, exist_ok=True)

def write_wav(filename, samples):
    with wave.open(filename, 'w') as wf:
        wf.setnchannels(1)        # mono
        wf.setsampwidth(2)        # 16-bit
        wf.setframerate(SAMPLE_RATE)
        data = bytearray()
        for s in samples:
            s_clamped = max(-1.0, min(1.0, s))
            val = int(s_clamped * 32767.0)
            data.extend(struct.pack('<h', val))
        wf.writeframes(data)
    print(f"Generated {os.path.basename(filename)}: {len(samples)/SAMPLE_RATE:.1f}s")

# 1. POLICE INTERCEPTOR (High-speed rapid alternating dual wail, 700Hz - 1500Hz)
def gen_police():
    duration = 3.6
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    phase = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        cycle = (t % 0.6) / 0.6
        lfo = 2.0 * cycle if cycle < 0.5 else 2.0 * (1.0 - cycle)
        freq = 700.0 + lfo * 800.0
        phase += 2.0 * math.pi * freq / SAMPLE_RATE
        s = 0.7 * math.sin(phase) + 0.25 * math.sin(2.0 * phase) + 0.15 * math.sin(3.0 * phase)
        s = math.tanh(s * 1.5)
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_police.wav"), samples)

# 2. COLD WAR AIR RAID (Deep slow-pitch mechanical siren building up from 300Hz to 850Hz)
def gen_air_raid():
    duration = 4.0
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    phase = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        cycle = (t % 2.0) / 2.0
        lfo = 0.5 * (1.0 - math.cos(2.0 * math.pi * cycle))
        freq = 320.0 + lfo * 530.0
        phase += 2.0 * math.pi * freq / SAMPLE_RATE
        s = 0.6 * math.sin(phase) + 0.3 * math.sin(2 * phase) + 0.2 * math.sin(3 * phase) + 0.1 * math.sin(4 * phase)
        s = math.tanh(s * 1.6)
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_air_raid.wav"), samples)

# 3. BIOHAZARD CONTAINMENT (Aggressive, stuttering two-tone rapid strobe pulse)
def gen_biohazard():
    duration = 3.2
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    phase = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        sub_cycle = (t % 0.4)
        freq = 1100.0 if sub_cycle < 0.2 else 880.0
        phase += 2.0 * math.pi * freq / SAMPLE_RATE
        # Add square overtone and envelope
        s = math.sin(phase) + 0.35 * math.sin(3.0 * phase)
        # 10Hz tremolo stutter
        tremolo = 0.8 + 0.2 * math.sin(2.0 * math.pi * 10.0 * t)
        s = math.tanh(s * 1.8) * tremolo
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_biohazard.wav"), samples)

# 4. STADIUM AIR HORN (Heavy multi-harmonic brass train horn chord)
def gen_air_horn():
    duration = 3.5
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    chord = [277.18, 369.99, 440.0, 554.37]
    phases = [0.0] * len(chord)
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        envelope = 0.0
        if 0.0 <= t < 0.7:
            envelope = min(1.0, t / 0.03) * min(1.0, (0.7 - t) / 0.04)
        elif 0.9 <= t < 1.6:
            envelope = min(1.0, (t - 0.9) / 0.03) * min(1.0, (1.6 - t) / 0.04)
        elif 1.8 <= t < 3.4:
            envelope = min(1.0, (t - 1.8) / 0.03) * min(1.0, (3.4 - t) / 0.05)

        s = 0.0
        for idx, f in enumerate(chord):
            phases[idx] += 2.0 * math.pi * f / SAMPLE_RATE
            s += 0.3 * math.sin(phases[idx]) + 0.15 * math.sin(2 * phases[idx]) + 0.08 * math.sin(3 * phases[idx])

        s = math.tanh(s * 2.2) * envelope
        samples.append(s * 0.98)
    write_wav(os.path.join(raw_dir, "siren_air_horn.wav"), samples)

# 5. CYBERPUNK LASER GRID (Piercing high-to-low fast laser sweeps)
def gen_laser_alarm():
    duration = 3.0
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        local_t = t % 0.25
        frac = local_t / 0.25
        freq = 2200.0 - (frac ** 0.5) * 1600.0
        phase = 2.0 * math.pi * freq * local_t
        s = math.sin(phase) + 0.3 * math.sin(3.0 * phase)
        env = (1.0 - frac)
        s = math.tanh(s * 2.0) * env
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_laser_alarm.wav"), samples)

# 6. SUBMARINE DIVE KLAXON (Classic deep resonant ahooga klaxon pulse)
def gen_sub_dive():
    duration = 3.6
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        cycle_t = t % 1.2
        if cycle_t < 0.6:
            frac = cycle_t / 0.6
            freq = 280.0 + math.sin(math.pi * frac) * 220.0
            phase = 2.0 * math.pi * freq * cycle_t
            s = math.sin(phase) + 0.5 * math.sin(2 * phase) + 0.25 * math.sin(4 * phase)
            env = math.sin(math.pi * frac)
            s = math.tanh(s * 2.0) * env
        else:
            s = 0.0
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_sub_dive.wav"), samples)

# 7. HIGH-SECURITY VAULT (Rapid ringing mechanical industrial klaxon)
def gen_bank_heist():
    duration = 3.0
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    phase1 = 0.0
    phase2 = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        # Ring bell at 12Hz rate
        bell_hit = (t * 12.0) % 1.0
        env = math.exp(-bell_hit * 5.0)
        phase1 += 2.0 * math.pi * 1480.0 / SAMPLE_RATE
        phase2 += 2.0 * math.pi * 1760.0 / SAMPLE_RATE
        s = (math.sin(phase1) + 0.8 * math.sin(phase2)) * env
        s = math.tanh(s * 1.8)
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_bank_heist.wav"), samples)

# 8. ALIEN INVASION (Sci-fi alien mothership FM frequency beam)
def gen_alien():
    duration = 3.5
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    phase = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        # FM modulation: carrier 750Hz, mod 14Hz, mod index 350
        mod = 350.0 * math.sin(2.0 * math.pi * 14.0 * t)
        freq = 750.0 + mod
        phase += 2.0 * math.pi * freq / SAMPLE_RATE
        s = math.sin(phase) + 0.3 * math.sin(2 * phase)
        s = math.tanh(s * 1.5)
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_alien_invasion.wav"), samples)

# 9. EURO FIRE TRUCK (Dual-tone High-Lo punchy alternating horn)
def gen_fire_truck():
    duration = 3.6
    num_samples = int(duration * SAMPLE_RATE)
    samples = []
    phase = 0.0
    for i in range(num_samples):
        t = i / SAMPLE_RATE
        is_high = (t % 0.8) < 0.4
        freq = 640.0 if is_high else 480.0
        phase += 2.0 * math.pi * freq / SAMPLE_RATE
        s = 0.8 * math.sin(phase) + 0.3 * math.sin(2 * phase) + 0.15 * math.sin(3 * phase)
        s = math.tanh(s * 1.6)
        samples.append(s * 0.95)
    write_wav(os.path.join(raw_dir, "siren_fire_truck.wav"), samples)

if __name__ == '__main__':
    gen_police()
    gen_air_raid()
    gen_biohazard()
    gen_air_horn()
    gen_laser_alarm()
    gen_sub_dive()
    gen_bank_heist()
    gen_alien()
    gen_fire_truck()
    print("All 9 custom high-intensity siren audio files successfully created!")
