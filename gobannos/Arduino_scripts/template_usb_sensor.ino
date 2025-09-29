/*
  template de communication avec Gobannos
*/

const int analogInPin = A0;  

int sensorValue = 0; 

void setup() {
  // initialize serial communications
  Serial.begin(115200);
}

void loop() {
  // read the analog in value:
  sensorValue = analogRead(analogInPin);

  // print the results to the Serial Monitor:
  Serial.write(sensorValue);

  // 8bits at 200Hz max
  delay(100); //8bits at 10hz
}
