import board
import digitalio
import random
import time
from threading import Thread, Event

LEFT_EYE_PIN = board.D24
RIGHT_EYE_PIN = board.D23


class Eyes:
    def __init__(
        self, blink_duration=0.1, min_interval=1.0, max_interval=4.0, auto_start=True
    ):
        self.left_eye = digitalio.DigitalInOut(LEFT_EYE_PIN)
        self.left_eye.direction = digitalio.Direction.OUTPUT

        self.right_eye = digitalio.DigitalInOut(RIGHT_EYE_PIN)
        self.right_eye.direction = digitalio.Direction.OUTPUT

        self.blink_duration = blink_duration
        self.min_interval = min_interval
        self.max_interval = max_interval

        self.steady_on = False
        self._stop_event = Event()
        self._thread = None

        # Défaut inchangé : le mode normal (marche) clignote dès l'instanciation.
        if auto_start:
            self.start_blink()

    def _set_eyes(self, state):
        self.left_eye.value = state
        self.right_eye.value = state

    def is_blinking(self):
        return self._thread is not None and self._thread.is_alive()

    def start_blink(self):
        if self.is_blinking():
            return
        self.steady_on = False
        self._stop_event.clear()
        self._thread = Thread(target=self.run, daemon=True)
        self._thread.start()

    def stop_blink(self):
        self._stop_event.set()
        if self._thread is not None:
            self._thread.join()
            self._thread = None

    def set_on(self):
        self.stop_blink()
        self._set_eyes(True)
        self.steady_on = True

    def set_off(self):
        self.stop_blink()
        self._set_eyes(False)
        self.steady_on = False

    def run(self):
        try:
            while not self._stop_event.is_set():
                self._set_eyes(False)
                if self._stop_event.wait(self.blink_duration):
                    break
                self._set_eyes(True)
                next_blink = random.uniform(self.min_interval, self.max_interval)
                if self._stop_event.wait(next_blink):
                    break
        except Exception as err:
            print(f"Error in eye thread: {err}")
            self._stop_event.set()

    def stop(self):
        self.stop_blink()
        self._set_eyes(False)
        self.left_eye.deinit()
        self.right_eye.deinit()


if __name__ == "__main__":
    e = Eyes()
    try:
        while True:
            time.sleep(1)
    finally:
        e.stop()
