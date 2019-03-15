#include "mbed.h"
#include "VL53L0X.h"
#include "ble/BLE.h"

#define range1_addr (0x56)
#define range2_addr (0x60)
#define range1_XSHUT   p17
#define range2_XSHUT   p18
#define VL53L0_I2C_SDA   p30
#define VL53L0_I2C_SCL   p7


 
Serial pc(USBTX, USBRX);
static DevI2C devI2c(VL53L0_I2C_SDA,VL53L0_I2C_SCL); 
DigitalOut led(LED1);
uint8_t pir_value = 2;
uint8_t resetCounter = 0;


Timer t;

uint16_t customServiceUUID  = 0xA000;
uint16_t readCharUUID      = 0xA001;

const static char     DEVICE_NAME[]        = "ChangeMe!!"; // change this
static const uint16_t uuid16_list[]        = {0xFFFF}; //Custom UUID, FFFF is reserved for development

static uint8_t readValue[12] = {0};
ReadOnlyArrayGattCharacteristic<uint8_t, sizeof(readValue)> readChar(readCharUUID, readValue,
                                GattCharacteristic::BLE_GATT_CHAR_PROPERTIES_READ |
                                GattCharacteristic::BLE_GATT_CHAR_PROPERTIES_NOTIFY );

/* Set up custom service */
GattCharacteristic *characteristics[] = {&readChar};
GattService customService(customServiceUUID, characteristics, sizeof(characteristics) / sizeof(GattCharacteristic *));


/*
 *  Restart advertising when phone app disconnects
*/
void disconnectionCallback(const Gap::DisconnectionCallbackParams_t *)
{
    BLE::Instance(BLE::DEFAULT_INSTANCE).gap().startAdvertising();
}
/*
 * Initialization callback
 */
void bleInitComplete(BLE::InitializationCompleteCallbackContext *params)
{
    BLE &ble          = params->ble;
    ble_error_t error = params->error;

    if (error != BLE_ERROR_NONE) {
        return;
    }

    ble.gap().onDisconnection(disconnectionCallback);

    /* Setup advertising */
    ble.gap().accumulateAdvertisingPayload(GapAdvertisingData::BREDR_NOT_SUPPORTED | GapAdvertisingData::LE_GENERAL_DISCOVERABLE); // BLE only, no classic BT
    ble.gap().setAdvertisingType(GapAdvertisingParams::ADV_CONNECTABLE_UNDIRECTED); // advertising type
    ble.gap().accumulateAdvertisingPayload(GapAdvertisingData::COMPLETE_LOCAL_NAME, (uint8_t *)DEVICE_NAME, sizeof(DEVICE_NAME)); // add name
    ble.gap().accumulateAdvertisingPayload(GapAdvertisingData::COMPLETE_LIST_16BIT_SERVICE_IDS, (uint8_t *)uuid16_list, sizeof(uuid16_list)); // UUID's broadcast in advertising packet
    ble.gap().setAdvertisingInterval(100); // 100ms.

    /* Add our custom service */
    ble.addService(customService);

    /* Start advertising */
    ble.gap().startAdvertising();
}


void init_buttons(void) {
    NRF_GPIO->PIN_CNF[2] = 0x08;
    //bez pull upa config
    //maybe the sensor is active low
}
void init_leds(void) {
    NRF_GPIO->DIRSET |= (0x01 << 21);
    
}
void led1_on(void) {
    NRF_GPIO->OUT &= ~(0x01 << 21); //clear bit 21 of OUT (turn on LED)
}

void led_off(void) {
    NRF_GPIO->OUT |= (0x01 << 21);
}

void sendStuff() {
    
    if(!(NRF_GPIO->IN >> 2 & 0x1)) {
        led1_on();
        pir_value = 0;
    } else {
        led_off();
        pir_value = 1;
    }
     
}

 
int main()
{   
    
    init_buttons();
    init_leds();
    led_off();
    t.start();

    BLE& ble = BLE::Instance(BLE::DEFAULT_INSTANCE);
    ble.init(bleInitComplete);

    /* SpinWait for initialization to complete. This is necessary because the
     * BLE object is used in the main loop below. */
    while (ble.hasInitialized()  == false) { /* spin loop */ }
    
    /*Contruct the sensors*/ 
    static DigitalOut shutdown1_pin(range1_XSHUT);
    static VL53L0X range1(&devI2c, &shutdown1_pin, NC);
    static DigitalOut shutdown2_pin(range2_XSHUT);
    static VL53L0X range2(&devI2c, &shutdown2_pin, NC);
    /*Initial all sensors*/   
    range1.init_sensor(range1_addr);
    range2.init_sensor(range2_addr);

    /*Get datas*/
    uint32_t distance1;
    uint32_t distance2;
    int status1;
    int status2;
    while(1){
        
        sendStuff();
        led=!led;
        
        status1 = range1.get_distance(&distance1);
        
        if (status1 == VL53L0X_ERROR_NONE) {
            //printf("error %d", status1);
            //printf("Range1 [mm]:            %6ld\r\n", distance1);
            //printf("%s \n", ctime(&seconds));
            //printf("The time taken was %f seconds\n", t.read());
            
        } else {
            printf("Range1 [mm]:                --\r \n");
            printf("error:%d", status1);
        }

        status2 = range2.get_distance(&distance2);
        if (status2 == VL53L0X_ERROR_NONE) {
            //printf("Range2 [mm]:            %6ld\r\n", distance2);
            //printf("%s \n", ctime(&seconds));
            //printf("The time taken was %f seconds\n", t.read());
        } else {
            printf("Range2 [mm]:                --\r\n");
            printf("error:%d", status1);
        }
        
        if(status1 == -1) {
            printf("RESET!");
        }
        
        if(status1 == -1) {
            NVIC_SystemReset();
        }
        /*
        //resetting if crashes
        if((status1 != 0) && (status1 != -6)) {
             printf("error:%d", status1);
             resetCounter++;
             printf("Reset counter:%d/n", resetCounter);
        }
        if(resetCounter > 5) {
            printf("RESET!/n");
            resetCounter = 0;
            NVIC_SystemReset();
        }
        //end
        */
        memcpy(readValue, &distance1, sizeof(distance1));
        memcpy((readValue+4), &distance2, sizeof(distance2));
        memcpy((readValue+8), &pir_value, sizeof(pir_value));
        ble.updateCharacteristicValue(readChar.getValueHandle(), readValue, sizeof(readValue));               
    }
 
}
 