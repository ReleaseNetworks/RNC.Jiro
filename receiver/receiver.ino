#include <RadioLib.h>
#include <SPI.h>
#include <U8g2lib.h>
#include "protocol.h"

#define VEXT_PIN 36
#define OLED_RST 21
#define OLED_SDA 17
#define OLED_SCL 18

SX1262 radio = new Module(8, 14, 12, 13);
U8G2_SSD1306_128X64_NONAME_F_SW_I2C display(U8G2_R0, OLED_SCL, OLED_SDA, OLED_RST);

uint32_t rxCount = 0;
uint32_t rxErrors = 0;
uint32_t txToDeviceCount = 0;
float lastRSSI = 0;
float lastSNR = 0;
uint8_t lastType = 0;
unsigned long lastDisplayUpdate = 0;

const char* typeName(uint8_t type) {
  switch (type) {
    case PKT_CONFIG:   return "CONFIG";
    case PKT_DATA:     return "DATA";
    case PKT_CONTROL:  return "CONTROL";
    case PKT_REGISTER: return "REGISTER";
    case PKT_DISPLAY:  return "DISPLAY";
    default:           return "?";
  }
}

void renderStats() {
  display.clearBuffer();

  char line[32];
  display.drawStr(0, 11, "Receiver Stats");

  snprintf(line, sizeof(line), "Packets: %lu  Err: %lu", rxCount, rxErrors);
  display.drawStr(0, 25, line);

  snprintf(line, sizeof(line), "Rssi: %.4f dBm", lastRSSI);
  display.drawStr(0, 37, line);

  snprintf(line, sizeof(line), "snr:  %.4f dB", lastSNR);
  display.drawStr(0, 49, line);

  snprintf(line, sizeof(line), "Last: %s  tx:%lu", typeName(lastType), txToDeviceCount);
  display.drawStr(0, 61, line);

  display.sendBuffer();
}

void setup() {
  Serial.begin(115200);

  pinMode(VEXT_PIN, OUTPUT);
  digitalWrite(VEXT_PIN, LOW);
  delay(100);
  display.begin();
  display.setFont(u8g2_font_6x10_tr);

  SPI.begin(9, 11, 10, 8);

  int state = radio.begin(868.0, 125.0, 9, 7, 0x12, 14, 8, 1.8);
  radio.setDio2AsRfSwitch(true);
  if (state != RADIOLIB_ERR_NONE) {
    display.clearBuffer();
    display.drawStr(0, 15, "init failed");
    display.setCursor(0, 35);
    display.print(state);
    display.sendBuffer();
    while (true) delay(1000);
  }

  display.clearBuffer();
  display.drawStr(0, 15, "Receiver init completed");
  display.sendBuffer();
}

void loop() {
  uint8_t rxBuf[80];
  int state = radio.receive(rxBuf, sizeof(rxBuf));

  if (state == RADIOLIB_ERR_NONE) {
    size_t len = radio.getPacketLength();
    Serial.write(rxBuf, len);

    rxCount++;
    lastRSSI = radio.getRSSI();
    lastSNR = radio.getSNR();

    if (len >= 2 && rxBuf[0] == FRAME_START) {
      lastType = rxBuf[1];
    }
  } else if (state != RADIOLIB_ERR_RX_TIMEOUT) {
    rxErrors++;
  }

  static uint8_t buf[80];
  static size_t pos = 0;
  while (Serial.available()) {
    uint8_t b = Serial.read();
    if (pos == 0 && b != FRAME_START) continue;
    buf[pos++] = b;
    if (pos >= 3) {
      size_t total = 4 + buf[2];
      if (pos == total) {
        radio.transmit(buf, total);
        txToDeviceCount++;
        pos = 0;
      }
    }
  }

  if (millis() - lastDisplayUpdate > 300) {
    renderStats();
    lastDisplayUpdate = millis();
  }
}
