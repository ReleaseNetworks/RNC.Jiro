#pragma once
#include <Arduino.h>

#define FRAME_START 0xAA
#define MAX_FIELDS 16

enum PacketType : uint8_t { PKT_CONFIG = 0x01, PKT_DATA = 0x02, PKT_CONTROL = 0x03, PKT_REGISTER = 0x04, PKT_DISPLAY = 0x05 };
enum FieldType  : uint8_t { TYPE_UINT8 = 0, TYPE_INT16 = 1, TYPE_FLOAT = 2, TYPE_BOOL = 3 };
enum FieldRole  : uint8_t { ROLE_INPUT = 0, ROLE_OUTPUT = 1 };
enum DisplayMode : uint8_t { DISPLAY_TEXT = 0, DISPLAY_FIELDS = 1 };

#define MAX_DISPLAY_FIELDS 8

struct DisplayRequest {
  DisplayMode mode = DISPLAY_TEXT;
  char text[64] = {0};
  uint8_t fieldIds[MAX_DISPLAY_FIELDS];
  uint8_t fieldCount = 0;
};

struct Field {
  uint8_t id;
  FieldType type;
  FieldRole role;
  char name[16];
  float lastValue = 0;
  bool used = false;
};

Field fields[MAX_FIELDS];

uint8_t fieldTypeSize(FieldType t) {
  switch (t) {
    case TYPE_UINT8: return 1;
    case TYPE_INT16: return 2;
    case TYPE_FLOAT: return 4;
    case TYPE_BOOL:  return 1;
  }
  return 0;
}

uint8_t checksum(uint8_t* data, size_t len) {
  uint8_t c = 0;
  for (size_t i = 0; i < len; i++) c ^= data[i];
  return c;
}

size_t buildFrame(uint8_t* buf, uint8_t type, uint8_t* payload, uint8_t payloadLen) {
  buf[0] = FRAME_START;
  buf[1] = type;
  buf[2] = payloadLen;
  memcpy(&buf[3], payload, payloadLen);
  buf[3 + payloadLen] = checksum(&buf[1], payloadLen + 2);
  return payloadLen + 4;
}

bool parseFrame(uint8_t* buf, size_t len, uint8_t &type, uint8_t* payloadOut, uint8_t &payloadLen) {
  if (len < 4 || buf[0] != FRAME_START) return false;
  type = buf[1];
  payloadLen = buf[2];
  if (len < (size_t)(4 + payloadLen)) return false;
  if (checksum(&buf[1], payloadLen + 2) != buf[3 + payloadLen]) return false;
  memcpy(payloadOut, &buf[3], payloadLen);
  return true;
}

int registerField(uint8_t id, const char* name, FieldType type, FieldRole role) {
  if (id >= MAX_FIELDS) return -1;
  fields[id].id = id;
  fields[id].type = type;
  fields[id].role = role;
  strncpy(fields[id].name, name, sizeof(fields[id].name) - 1);
  fields[id].name[sizeof(fields[id].name) - 1] = '\0';
  fields[id].used = true;
  return id;
}

void setFieldValue(uint8_t id, float value) {
  if (id < MAX_FIELDS) fields[id].lastValue = value;
}

void encodeFieldValue(Field &f, uint8_t* out) {
  switch (f.type) {
    case TYPE_UINT8: { uint8_t v = (uint8_t)f.lastValue; memcpy(out, &v, 1); break; }
    case TYPE_INT16: { int16_t v = (int16_t)f.lastValue; memcpy(out, &v, 2); break; }
    case TYPE_FLOAT: { memcpy(out, &f.lastValue, 4); break; }
    case TYPE_BOOL:  { uint8_t v = f.lastValue != 0 ? 1 : 0; memcpy(out, &v, 1); break; }
  }
}

float decodeFieldValue(FieldType type, uint8_t* raw) {
  switch (type) {
    case TYPE_UINT8: return (float)raw[0];
    case TYPE_INT16: { int16_t v; memcpy(&v, raw, 2); return (float)v; }
    case TYPE_FLOAT: { float v; memcpy(&v, raw, 4); return v; }
    case TYPE_BOOL:  return (float)raw[0];
  }
  return 0;
}

size_t buildConfigPayload(uint8_t* buf, Field &f) {
  buf[0] = f.id;
  buf[1] = f.type;
  buf[2] = f.role;
  uint8_t nameLen = strlen(f.name);
  buf[3] = nameLen;
  memcpy(&buf[4], f.name, nameLen);
  return 4 + nameLen;
}

void sendAllConfigs(SX1262 &radio) {
  for (int i = 0; i < MAX_FIELDS; i++) {
    if (!fields[i].used) continue;
    uint8_t payload[32];
    size_t payloadLen = buildConfigPayload(payload, fields[i]);
    uint8_t frame[40];
    size_t frameLen = buildFrame(frame, PKT_CONFIG, payload, payloadLen);
    radio.transmit(frame, frameLen);
    delay(50);
  }
}

void sendData(SX1262 &radio) {
  uint8_t payload[64];
  uint8_t count = 0;
  size_t offset = 1;
  for (int i = 0; i < MAX_FIELDS; i++) {
    if (!fields[i].used || fields[i].role != ROLE_INPUT) continue;
    uint8_t size = fieldTypeSize(fields[i].type);
    payload[offset++] = fields[i].id;
    payload[offset++] = size;
    encodeFieldValue(fields[i], &payload[offset]);
    offset += size;
    count++;
  }
  payload[0] = count;

  uint8_t frame[80];
  size_t frameLen = buildFrame(frame, PKT_DATA, payload, offset);
  radio.transmit(frame, frameLen);
}

void handleControlPayload(uint8_t* payload, uint8_t len) {
  uint8_t count = payload[0];
  size_t offset = 1;
  for (int i = 0; i < count; i++) {
    uint8_t id = payload[offset++];
    uint8_t size = payload[offset++];
    if (id < MAX_FIELDS) {
      fields[id].lastValue = decodeFieldValue(fields[id].type, &payload[offset]);
    }
    offset += size;
  }
}

bool handleRegisterPayload(uint8_t* payload, uint8_t len, Field &outField) {
  if (len < 4) return false;
  uint8_t id = payload[0];
  if (id >= MAX_FIELDS) return false;
  FieldType type = (FieldType)payload[1];
  FieldRole role = (FieldRole)payload[2];
  uint8_t nameLen = payload[3];
  if (nameLen >= sizeof(fields[id].name)) nameLen = sizeof(fields[id].name) - 1;

  char name[16] = {0};
  memcpy(name, &payload[4], nameLen);

  registerField(id, name, type, role);
  outField = fields[id];
  return true;
}

bool handleDisplayPayload(uint8_t* payload, uint8_t len, DisplayRequest &outReq) {
  if (len < 1) return false;
  uint8_t mode = payload[0];

  if (mode == DISPLAY_TEXT) {
    if (len < 2) return false;
    uint8_t textLen = payload[1];
    if (len < (uint8_t)(2 + textLen)) return false;
    if (textLen >= sizeof(outReq.text)) textLen = sizeof(outReq.text) - 1;
    memset(outReq.text, 0, sizeof(outReq.text));
    memcpy(outReq.text, &payload[2], textLen);
    outReq.mode = DISPLAY_TEXT;
    outReq.fieldCount = 0;
    return true;
  }

  if (mode == DISPLAY_FIELDS) {
    if (len < 2) return false;
    uint8_t count = payload[1];
    if (count > MAX_DISPLAY_FIELDS) count = MAX_DISPLAY_FIELDS;
    if (len < (uint8_t)(2 + count)) return false;
    for (uint8_t i = 0; i < count; i++) outReq.fieldIds[i] = payload[2 + i];
    outReq.fieldCount = count;
    outReq.mode = DISPLAY_FIELDS;
    return true;
  }

  return false;
}

size_t buildDisplayTextPayload(uint8_t* buf, const char* text) {
  uint8_t textLen = strlen(text);
  if (textLen > 60) textLen = 60;
  buf[0] = DISPLAY_TEXT;
  buf[1] = textLen;
  memcpy(&buf[2], text, textLen);
  return 2 + textLen;
}

size_t buildDisplayFieldsPayload(uint8_t* buf, uint8_t* fieldIds, uint8_t count) {
  if (count > MAX_DISPLAY_FIELDS) count = MAX_DISPLAY_FIELDS;
  buf[0] = DISPLAY_FIELDS;
  buf[1] = count;
  memcpy(&buf[2], fieldIds, count);
  return 2 + count;
}
