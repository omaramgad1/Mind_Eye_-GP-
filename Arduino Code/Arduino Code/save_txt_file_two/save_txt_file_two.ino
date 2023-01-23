#define BUTTON_PIN 13

#include "esp_camera.h"
#include "Arduino.h"
#include "FS.h"                // SD Card ESP32
#include "SD_MMC.h"            // SD Card ESP32
#include "soc/soc.h"           // Disable brownour problems
#include "soc/rtc_cntl_reg.h"  // Disable brownour problems
#include "driver/rtc_io.h"

#define CAMERA_MODEL_AI_THINKER
#include "camera_pins.h"
#include "BluetoothSerial.h"

#include <EEPROM.h>            // read and write from flash memory
#include <base64.h>

#define EEPROM_SIZE 1

BluetoothSerial SerialBT;
byte butLst;
enum { None, SingleClick, DoubleClick };

int pictureNumber = 0;
bool BTisConnected = false;


void setup() {

  pinMode (BUTTON_PIN, INPUT_PULLUP);
  butLst = digitalRead (BUTTON_PIN);
  Serial.begin(115200);


}


//// Bluetooth

void initBT() {
  if (!SerialBT.begin("ESP32CAM-CLASSIC-BT")) {
    Serial.println("An error occurred initializing Bluetooth");
    ESP.restart();
  } else {
    Serial.println("Bluetooth initialized");
  }

  SerialBT.register_callback(btCallback);
  Serial.println("The device started, now you can pair it with bluetooth");
}

void btCallback(esp_spp_cb_event_t event, esp_spp_cb_param_t *param) {
  if (event == ESP_SPP_SRV_OPEN_EVT) {
    Serial.println("Client Connected!");
    BTisConnected = true;
    sensor_t *s = esp_camera_sensor_get();

    s->set_framesize(s, FRAMESIZE_XGA);
    s->set_brightness(s, 0);     // -2 to 2
    s->set_contrast(s, 0);       // -2 to 2
    s->set_saturation(s, 0);     // -2 to 2
    s->set_special_effect(s, 0); // 0 to 6 (0 - No Effect, 1 - Negative, 2 - Grayscale, 3 - Red Tint, 4 - Green Tint, 5 - Blue Tint, 6 - Sepia)
    s->set_whitebal(s, 1);
    s->set_awb_gain(s, 1);       // 0 = disable , 1 = enable
    s->set_wb_mode(s, 0);
    s->set_wpc(s, 1);            // 0 = disable , 1 = enable
    s->set_raw_gma(s, 1);        // 0 = disable , 1 = enable
    s->set_lenc(s, 1);
    s->set_dcw(s, 1);

  } else if (event == ESP_SPP_DATA_IND_EVT) {
    Serial.printf("ESP_SPP_DATA_IND_EVT len=%d, handle=%d\n\n", param->data_ind.len, param->data_ind.handle);
    if (SerialBT.available()) {
      Serial.write(SerialBT.read());
    }
  }
}


void writeSerialBT(String img64) {
  SerialBT.print(img64);
}




///Camera

void initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.frame_size = FRAMESIZE_XGA;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 12;
  config.fb_count = 1;

  // if PSRAM IC present, init with UXGA resolution and higher JPEG quality
  //                      for larger pre-allocated frame buffer.
  if(config.pixel_format == PIXFORMAT_JPEG){ 
    if(psramFound()){
      config.jpeg_quality = 10;
      config.fb_count = 2;
      config.grab_mode = CAMERA_GRAB_LATEST;
    } else {
      // Limit the frame size when PSRAM is not available
      config.frame_size = FRAMESIZE_SVGA;
      config.fb_location = CAMERA_FB_IN_DRAM;
    }
  }

  // Init Camera
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x", err);
    return;
  }
}

void initSDcard() {
  //Serial.println("Starting SD Card");
  if (!SD_MMC.begin()) {
    Serial.println("SD Card Mount Failed");
    return;
  }

  uint8_t cardType = SD_MMC.cardType();
  if (cardType == CARD_NONE) {
    Serial.println("No SD Card attached");
    return;
  }

  // initialize EEPROM with predefined size
  EEPROM.begin(EEPROM_SIZE);
  pictureNumber = EEPROM.read(0) + 1;

}

/// SD Card

void saveImageToSD() {



  camera_fb_t * fb = NULL;

  // Take Picture with Camera
  fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("Camera capture failed");
    ESP.restart();
    return;
  }

  // Path where new picture will be saved in SD Card
  String path = "/picture" + String(pictureNumber) + ".jpg";

  fs::FS &fs = SD_MMC;
  Serial.printf("Picture file name: %s\n", path.c_str());


  /////
  /////
  File file2 = fs.open("/test.txt", FILE_WRITE);
  if (!file2) {
    Serial.println("Opening file failed for text");
    return;
  }
  String encrypt = base64::encode(fb->buf, fb->len);
  String myString = String(encrypt.length());
  writeSerialBT(myString);
  if (file2.print(encrypt)) {
    Serial.println("File write success");
  } else {
    Serial.println("File write failed");
  }
  file2.close();
  pictureNumber = pictureNumber + 1;
  delay(500);
  writeSerialBT(encrypt);
  /////
  /////


  File file = fs.open(path.c_str(), FILE_WRITE);
  if (!file) {
    Serial.println("Failed to open file in writing mode for image");
  }
  else {
    file.write(fb->buf, fb->len); // payload (image), payload length
    Serial.printf("Saved file to path: %s\n", path.c_str());
    EEPROM.write(0, pictureNumber);
    EEPROM.commit();
  }
  file.close();

  esp_camera_fb_return(fb);

}

int chkButton (void)
{
  const  unsigned long ButTimeout  = 250;
  static unsigned long msecLst;
  unsigned long msec = millis ();

  if (msecLst && (msec - msecLst) > ButTimeout)  {
    msecLst = 0;
    return SingleClick;
  }

  byte but = digitalRead (BUTTON_PIN);
  if (butLst != but)  {
    butLst = but;
    delay (10);           // **** debounce

    if (LOW == but)  {   // press
      if (msecLst)  { // 2nd press
        msecLst = 0;
        return DoubleClick;
      }
      else
        msecLst = 0 == msec ? 1 : msec;
    }
  }

  return None;
}


void loop() {
  switch (chkButton ())  {
    case SingleClick:
      Serial.println ("single");
      if (BTisConnected)
        saveImageToSD();
      break;

    case DoubleClick:
      Serial.println ("double");
      if (BTisConnected) {
        ESP.restart();
      } else {
        initBT();
        initCamera();
        initSDcard();
      }

      break;
  }
  if (Serial.available()) {
    SerialBT.write(Serial.read());
  }
}
