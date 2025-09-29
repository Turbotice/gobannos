const int analogInPin0 = A0;  
const int analogInPin2 = A2;  
char data[2];
int sensorValue = 0; 
int status = 0;

void setup() {
  // initialize serial communications
  Serial.begin(115200);
  pinMode(LED_BUILTIN, OUTPUT);

}

void loop() {
  if (Serial.available()) {
    sensorValue = -10;//data[0];
    int bytesRead = Serial.readBytes(data, 1);
    data[bytesRead] = '\0';  // Null-terminate the string
    //Serial.print("Received: ");
    //Serial.println(data);
    digitalWrite(LED_BUILTIN, HIGH);  // turn the LED on (HIGH is the voltage level)
    status = 1;
  //analogRead(analogInPin);
  // print the results to the Serial Monitor:
    delay(1000); //8bits at 10hz
  }
  //digitalWrite(LED_BUILTIN, HIGH);  // turn the LED on (HIGH is the voltage level)
  digitalWrite(LED_BUILTIN, LOW);   // turn the LED off by making the voltage LOW
  Serial.write(sensorValue);
  delay(200); //8bits at 10hz
  sensorValue = 10;
}
