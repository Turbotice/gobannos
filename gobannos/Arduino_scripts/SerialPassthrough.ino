

void setup() {
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);
}

int ledState = LOW;  // état actuel de la LED

void loop() {

  char rec[10];
  int i=0;
  int j=0;
  int led= 0x1;

  int once = 1;

  while (Serial.available()) {        // If anything comes in Serial (USB),
    rec[i] = Serial.read();  // read it and send it out Serial
    if (rec[i] == 't') {
      ledState = (ledState == LOW) ? HIGH : LOW;
      digitalWrite(LED_BUILTIN, ledState);
    }
    i++;
  }

  for(j=0; j<i; j++){
    Serial.write(rec[j]);
  }

}
