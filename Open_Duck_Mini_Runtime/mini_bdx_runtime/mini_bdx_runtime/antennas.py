import math
import time

# BCM : board.D13 / board.D12 (identique au HAT, hors I2S 18/19/21).
LEFT_GPIO = 13
RIGHT_GPIO = 12
LEFT_SIGN = 1
RIGHT_SIGN = -1
MIN_UPDATE_INTERVAL = 1 / 50  # 20ms
PWM_FREQ_HZ = 50
CENTER_SETTLE_S = 0.4


def value_to_duty_percent(v):
    """SG90 : 1 ms = 0°, 1,5 ms = 90°, 2 ms = 180° sur une période 20 ms."""
    pulse_width_ms = 1.5 + (v * 0.5)
    percent = (pulse_width_ms / 20.0) * 100.0
    return min(max(percent, 5.0), 10.0)


def value_to_duty_cycle(v):
    """Ancien format pwmio 16 bits (repli)."""
    pulse_width_ms = 1.5 + (v * 0.5)
    duty_cycle = int((pulse_width_ms / 20) * 65535)
    return min(max(duty_cycle, 3277), 6553)


class Antennas:
    def __init__(self):
        self._lgpio = None
        self._h = None
        self.pwm_left = None
        self.pwm_right = None
        if not self._init_lgpio():
            self._init_pwmio()

    @property
    def backend(self):
        return "lgpio" if self._h is not None else "pwmio"

    def _init_lgpio(self):
        """PWM matériel (lgpio) : pas le PWM logiciel Blinka, source du tremblement."""
        try:
            import lgpio
        except ImportError:
            return False
        last_err = None
        for chip in (0, 4):
            handle = None
            try:
                handle = lgpio.gpiochip_open(chip)
                lgpio.gpio_claim_output(handle, LEFT_GPIO)
                lgpio.gpio_claim_output(handle, RIGHT_GPIO)
                self._lgpio = lgpio
                self._h = handle
                return True
            except Exception as err:
                last_err = err
                if handle is not None:
                    try:
                        lgpio.gpiochip_close(handle)
                    except Exception:
                        pass
        if last_err is not None:
            print(f"lgpio PWM indisponible ({last_err}), repli pwmio.")
        return False

    def _init_pwmio(self):
        import board
        import pwmio

        LEFT_ANTENNA_PIN = board.D13
        RIGHT_ANTENNA_PIN = board.D12
        neutral_duty = value_to_duty_cycle(0)
        self.pwm_left = pwmio.PWMOut(
            LEFT_ANTENNA_PIN, frequency=PWM_FREQ_HZ, duty_cycle=neutral_duty
        )
        self.pwm_right = pwmio.PWMOut(
            RIGHT_ANTENNA_PIN, frequency=PWM_FREQ_HZ, duty_cycle=neutral_duty
        )

    def set_position_left(self, position):
        self.set_position(LEFT_GPIO, position, LEFT_SIGN, pwm=self.pwm_left)

    def set_position_right(self, position):
        self.set_position(RIGHT_GPIO, position, RIGHT_SIGN, pwm=self.pwm_right)

    def set_position(self, gpio, value, sign=1, pwm=None):
        if not (-1 <= value <= 1):
            print("Invalid input! Enter a value between -1 and 1.")
            return
        if self._h is not None:
            self._lgpio.tx_pwm(
                self._h, gpio, PWM_FREQ_HZ, value_to_duty_percent(value * sign)
            )
            return
        if pwm is None:
            return
        pwm.duty_cycle = value_to_duty_cycle(value * sign)

    def set_center(self, settle_s=CENTER_SETTLE_S):
        """90° servo = 1,5 ms, puis attente d'arrivée (PWM encore actif)."""
        self.set_position_left(0)
        self.set_position_right(0)
        if settle_s > 0:
            time.sleep(settle_s)

    def oscillate(self, duration=2.0, frequency=1.0):
        """Oscillation, puis centre. L'appelant coupe le PWM (stop)."""
        start_time = time.monotonic()
        current_time = start_time
        while current_time - start_time < duration:
            value = math.sin(2 * math.pi * frequency * current_time)
            self.set_position_left(value)
            self.set_position_right(value)
            time.sleep(MIN_UPDATE_INTERVAL)
            current_time = time.monotonic()
        self.set_center()

    def stop(self):
        """Coupe les impulsions PWM (plus de couple / plus de tremblement logiciel)."""
        if self._h is not None:
            try:
                self._lgpio.tx_pwm(self._h, LEFT_GPIO, 0, 0)
                self._lgpio.tx_pwm(self._h, RIGHT_GPIO, 0, 0)
            except Exception:
                pass
            try:
                self._lgpio.gpiochip_close(self._h)
            except Exception:
                pass
            self._h = None
            return
        if self.pwm_left is not None:
            time.sleep(MIN_UPDATE_INTERVAL)
            try:
                self.pwm_left.deinit()
                self.pwm_right.deinit()
            except Exception:
                pass
            self.pwm_left = None
            self.pwm_right = None


if __name__ == "__main__":
    antennas = Antennas()
    try:
        antennas.oscillate(duration=5.0, frequency=1.0)
    finally:
        antennas.stop()
