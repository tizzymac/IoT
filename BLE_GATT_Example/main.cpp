#include "mbed.h"
#include "ble/BLE.h"

 
Serial pc(USBTX, USBRX);

DigitalOut led(LED1);
uint8_t pir_value1 = 2;
uint8_t pir_value2 = 2;
//CHANGE: ADD NEW VARIABLES
uint8_t resetCounter = 0;


Timer t;

uint16_t customServiceUUID  = 0xA000;
uint16_t readCharUUID      = 0xA001;

const static char     DEVICE_NAME[]        = "ChangeMe!!"; // change this
static const uint16_t uuid16_list[]        = {0xFFFF}; //Custom UUID, FFFF is reserved for development

static uint8_t readValue[4] = {0}; //CHANGE:ADD 2 FOR EACH NEW SENSOR TO readValue[4]
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
    NRF_GPIO->PIN_CNF[3] = 0x08;
    //CHANGE: ADD PIN DEFINITION: NRF_GPIO->PIN_CNF[3] MEANS PIN 3

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
        pir_value1 = 0;
    } else {
        led_off();
        pir_value1 = 1;
    }
    
    if(!(NRF_GPIO->IN >> 3 & 0x1)) {
        led1_on();
        pir_value2 = 0;
    } else {
        led_off();
        pir_value2 = 1;
    }
    //CHANGE: ADD A CONDITION FOR EACH NEW SENSOR
     
}

 
int main()
{   
    
    init_buttons();
    init_leds();
    led_off();

    BLE& ble = BLE::Instance(BLE::DEFAULT_INSTANCE);
    ble.init(bleInitComplete);

    /* SpinWait for initialization to complete. This is necessary because the
     * BLE object is used in the main loop below. */
    while (ble.hasInitialized()  == false) { /* spin loop */ }
    
    while(1){
        
        sendStuff();
        led=!led;
        
        memcpy(readValue, &pir_value1, sizeof(pir_value1));
        memcpy((readValue+2), &pir_value2, sizeof(pir_value2));
        //CHANGE: ADD ANOTHER LINE OF THOSE ABOVE
        ble.updateCharacteristicValue(readChar.getValueHandle(), readValue, sizeof(readValue));               
    }
 
}
 