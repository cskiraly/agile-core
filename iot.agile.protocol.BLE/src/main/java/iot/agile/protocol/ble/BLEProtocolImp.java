/*
 * Copyright 2016 Dagmawi Neway Mekuria <d.mekuria@create-net.org>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package iot.agile.protocol.ble;

import java.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iot.agile.Protocol;
import iot.agile.ProtocolManager;
import iot.agile.object.AbstractAgileObject;
import iot.agile.object.DeviceOverview;
import iot.agile.object.DeviceStatusType;
import iot.agile.object.StatusType;
import tinyb.BluetoothDevice;
import tinyb.BluetoothException;
import tinyb.BluetoothGattCharacteristic;
import tinyb.BluetoothGattService;
import tinyb.BluetoothManager;
import tinyb.BluetoothNotification;
import tinyb.BluetoothType;

/**
 * Agile Bluetooth Low Energy(BLE) Protocol implementation
 *
 * @author dagi
 *
 */
public class BLEProtocolImp extends AbstractAgileObject implements Protocol {

	protected final Logger logger = LoggerFactory.getLogger(BLEProtocolImp.class);

	/**
	 * Bus name for AGILE BLE Protocol
	 */
	private static final String AGILE_BLUETOOTH_BUS_NAME = "iot.agile.protocol.BLE";

	/**
	 * Bus path for AGILE BLE Protocol
	 */
	private static final String AGILE_BLUETOOTH_BUS_PATH = "/iot/agile/protocol/BLE";

	/**
	 * DBus bus path for found new device signal
	 */
	private static final String AGILE_NEW_DEVICE_SIGNAL_PATH = "/iot/agile/NewDevice";

	/**
	 * DBus bus path for for new record/data reading
	 */
	private static final String AGILE_NEW_RECORD_SIGNAL_PATH = "/iot/agile/NewRecord";

	/**
	 * Protocol name
	 */
	private static final String PROTOCOL_NAME = "Bluetooth Low Energy";

	/**
	 * Protocol driver name
	 */
	private static final String DRIVER_NAME = "BLE";

	// Device status
	public static final String CONNECTED = "CONNECTED";

	public static final String DISCONNECTED = "DISCONNECTED";

	public static final String AVAILABLE = "AVAILABLE";

	public static final String UNAVAILABLE = "AVAILABLE";

	private static final String GATT_SERVICE = "GATT_SERVICE";

	private static final String GATT_CHARACTERSTICS = "GATT_CHARACTERSTICS";

	private static final String PAYLOAD = "PAYLOAD";

	/**
	 * Device list
	 */
	protected List<DeviceOverview> deviceList = new ArrayList<DeviceOverview>();

	/**
	 * The bluetooth manager
	 */
	protected BluetoothManager bleManager;

	protected byte[] lastRecord;

	private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

	private ScheduledFuture future;

	protected final State state = new State();

	public class State {
		public boolean isDiscovering = false;
	}

	public static void main(String[] args) throws DBusException {
		Protocol bleProtocol = new BLEProtocolImp();
	}

	public BLEProtocolImp() throws DBusException {
		dbusConnect(AGILE_BLUETOOTH_BUS_NAME, AGILE_BLUETOOTH_BUS_PATH, this);
		try {
			bleManager = BluetoothManager.getBluetoothManager();
		} catch (BluetoothException bex) {
			logger.error(" Failed to start BLE Protocol, no bluetooth adapter found on the system", bex);
		} catch (Exception e) {
			logger.error("Error in getting BluetoothManager instance", e);
		}
		logger.debug("Started BLE Protocol");

		// fill initial list of devices
		List<BluetoothDevice> list = bleManager.getDevices();
		for (BluetoothDevice device : list) {
			logger.info("{}({}) Conn:{} RSSI:{}", device.getName(), device.getAddress(), device.getConnected(), device.getRSSI());
			if (device.getConnected()) {
				DeviceOverview deviceOverview = new DeviceOverview(device.getAddress(), AGILE_BLUETOOTH_BUS_NAME, device.getName(), CONNECTED);
				if (isNewDevice(deviceOverview)) {
					deviceList.add(deviceOverview);
					try {
						ProtocolManager.FoundNewDeviceSignal foundNewDevSig = new ProtocolManager.FoundNewDeviceSignal(AGILE_NEW_DEVICE_SIGNAL_PATH, deviceOverview);
						connection.sendSignal(foundNewDevSig);
					} catch (DBusException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.Protocol#name()
	 */
	@Override
	public String Name() {
		return PROTOCOL_NAME;
	}

	/**
	 *
	 * @see iot.agile.protocol.ble.Protocol#driver()
	 */
	@Override
	public String Driver() {
		return DRIVER_NAME;
	}

	/**
	 *
	 *
	 * @see iot.agile.protocol.ble.Protocol#Status()
	 */
	@Override
	public String Status() {
		logger.debug("Protocol.Status not implemented");
		return null;
	}

	/**
	 * Returns lists of devices
	 */
	@Override
	public List<DeviceOverview> Devices() {
		return deviceList;
	}

	/**
	 * @see iot.agile.protocol.ble.Protocol#DataStore()
	 */
	@Override
	public byte[] Data() {
		return lastRecord;
	}

	/**
	 * Connect BLE Device
	 *
	 * @param deviceAddress
	 * @throws DBusException
	 * @see iot.agile.protocol.ble.Protocol#initialize(java.lang.String)
	 */
	@Override
	public void Connect(String deviceAddress) throws DBusException {
		logger.info("Connecting to BLE device {}", deviceAddress);
		try {
			BluetoothDevice bleDevice = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress,
					null);
			if (bleDevice != null) {
				if (!bleDevice.getConnected()) {
					bleDevice.connect();
				}
			}
		} catch (Exception e) {
			logger.error("Failed to connect: {}", deviceAddress, e);
			throw new DBusException("Failed to connect device:" + deviceAddress);
		}
	}

	/**
	 *
	 * Disconnect bluetooth device
	 *
	 * @return
	 * @throws DBusException
	 * @see iot.agile.protocol.ble.Protocol#destory(java.lang.String)
	 */
	@Override
	public void Disconnect(String deviceAddress) throws DBusException {
		logger.info("Disconnecting from BLE device {}", deviceAddress);
		try {
			BluetoothDevice bleDevice = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress,
					null);
			if (bleDevice != null) {
				if (bleDevice.getConnected()) {
					bleDevice.disconnect();
				}
			}
		} catch (Exception e) {
			logger.error("Failed to disconnect {}", deviceAddress, e);
			throw new DBusException("Failed to disconnect device:" + deviceAddress);
		}
	}

	/**
	 * Discover status
	 *
	 * @see iot.agile.protocol.ble.Protocol#StopDiscovery()
	 */
	@Override
	public String DiscoveryStatus() {
		if(future != null){
			if(future.isCancelled()){
				return "NONE";
			}else{
				return "RUNNING";
			}	
		}
		return "NONE";
	}

	/**
	 * Discover BLE devices, towards a more descriptive name
	 */
	@Override
	public void StartDiscovery() {
		logger.info("Started discovery of BLE devices");

		bleManager.startDiscovery();

		/* TODO: would be better reactive: lister to bluez/TinyB signals */
		Runnable task = () -> {

			logger.debug("Checking for new devices");

			int newDevices = 0;
			List<BluetoothDevice> list = bleManager.getDevices();
			for (BluetoothDevice device : list) {
				if (device.getRSSI() != 0 || device.getConnected()) {
					DeviceOverview deviceOverview = new DeviceOverview(device.getAddress(), AGILE_BLUETOOTH_BUS_NAME,
							device.getName(), AVAILABLE);
					if (isNewDevice(deviceOverview)) {
						logger.info("{}({}) Conn:{} RSSI:{}", device.getName(), device.getAddress(), device.getConnected(), device.getRSSI());
						deviceList.add(deviceOverview);
						try {
							ProtocolManager.FoundNewDeviceSignal foundNewDevSig = new ProtocolManager.FoundNewDeviceSignal(
									AGILE_NEW_DEVICE_SIGNAL_PATH, deviceOverview);
							connection.sendSignal(foundNewDevSig);
						} catch (DBusException e) {
							e.printStackTrace();
						}
						printDevice(device);
						newDevices++;
					}
				}
			}

			if (newDevices > 0) {
				logger.info("Found {} new device(s)", newDevices);
			}
		};

		future = executor.scheduleWithFixedDelay(task, 0, 1, TimeUnit.SECONDS);
	}

	/**
	 * (non-Javadoc)
	 *
	 * @see iot.agile.protocol.ble.Protocol#StopDiscovery()
	 */
	@Override
	public void StopDiscovery() {
		bleManager.stopDiscovery();
		if (future != null) {
			future.cancel(true);
		}
	}

	/**
	 *
	 *
	 * @throws DBusException
	 * @see iot.agile.protocol.ble.Protocol#write()
	 */
	@Override
	public synchronized void Write(String deviceAddress, Map<String, String> profile, byte[] payload) throws DBusException {
			BluetoothDevice device;
			try {
				device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
				if (device != null) {
					if (device.getConnected()) {
						BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
						if (gattService != null) {
							BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
							if (gattChar != null) {
								gattChar.writeValue(payload);
							} else {
								logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
							}
						} else {
							logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
						}
					} else {
						logger.error("Device not connected: {}", deviceAddress);
					}
				} else {
					logger.error("Device not found: {}", deviceAddress);
				}

			} catch (Exception e) {
				logger.error("Failed to write value: {}", deviceAddress, e);
					throw new DBusException("Failed to write value:" + deviceAddress);
			}
		}

	/**
	 *
	 *
	 * @param profile
	 * @see iot.agile.protocol.ble.Protocol#read()
	 */
	public synchronized byte[] Read(String deviceAddress, Map<String, String> profile) throws DBusException {
		BluetoothDevice device;
		try {
			device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
			if (device != null) {
				if (device.getConnected()) {
					BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
					if (gattService != null) {
						BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
						if (gattChar != null) {
							lastRecord = gattChar.readValue();
							return lastRecord;
						} else {
							logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
						}
					} else {
						logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
					}
				} else {
					logger.error("Device not connected: {}", deviceAddress);
				}
			} else {
				logger.error("Device not found: {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to read ", e);
			throw new DBusException("Failed to read value");
		}
		return null;
	}

	/**
	 * Workaround for a TinyB issue: when TinyB is using BluetoothGattService.find() to get a copy of BluetoothGattCharacteristics,
	 * it is returning a new object with a new notification callback attibute inside. This can lead to notfication callback objects
	 * remaining linked even after disabling notifcation, leading later (after enabling notifications again) to multiple callbacks.
	 */
	private class AddressProfile extends Vector<Object> {
		public AddressProfile(String a, Map<String,String> p) {
			add(a);
			add(p);
		}
	}

	private Map<AddressProfile, BluetoothGattCharacteristic> subscriptions = new HashMap< AddressProfile, BluetoothGattCharacteristic>();

	/**
	 * @throws DBusException
	 * 
	 */
	@Override
	public void Subscribe(String deviceAddress, Map<String, String> profile) throws DBusException {
		BluetoothDevice device;
		try {
			logger.info("Enabling notification on {}", deviceAddress);
			device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
			if (device != null) {
				if (device.getConnected()) {
					BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
					if (gattService != null) {
						BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
						if (gattChar != null) {
							if(!gattChar.getNotifying()){
								gattChar.enableValueNotifications(new NewRecordNotification(deviceAddress,profile));
								subscriptions.put(new AddressProfile(deviceAddress, profile), gattChar);
							}
						} else {
							logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
						}
					} else {
						logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
					}
				} else {
					logger.error("Device not connected: {}", deviceAddress);
				}
			} else {
				logger.error("Device not found: {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to subscribe ", e);
			throw new DBusException("Failed to subscribe");
		}
	}
	
	
	@Override
	public void Unsubscribe(String deviceAddress, Map<String, String> profile) throws DBusException {
		BluetoothDevice device;
		try {
			logger.info("Disabling notification on {}", deviceAddress);
			device = (BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null);
			if (device != null) {
				if (device.getConnected()) {
					BluetoothGattService gattService = device.find(profile.get(GATT_SERVICE));
					if (gattService != null) {
						//BluetoothGattCharacteristic gattChar = gattService.find(profile.get(GATT_CHARACTERSTICS));
						BluetoothGattCharacteristic gattChar = subscriptions.remove(new AddressProfile(deviceAddress, profile));
						if (gattChar != null) {
							if(gattChar.getNotifying()){
								logger.info("disabled");
								gattChar.disableValueNotifications();
							}
						} else {
							logger.error("The device does not have {} gatt characterstics", profile.get(GATT_SERVICE));
						}
					} else {
						logger.error("The device does not {} have gatt service", profile.get(GATT_CHARACTERSTICS));
					}
				} else {
					logger.error("Device not connected: {}", deviceAddress);
				}
			} else {
				logger.error("Device not found: {}", deviceAddress);
			}
		} catch (Exception e) {
			logger.error("Failed to unsubscribe ", e);
			throw new DBusException("Failed to unsubscribe");
		}
	}

	/**
	 * 
	 * @author dagi
	 * 
	 *         New record signal for Subscription
	 */
	protected class NewRecordNotification implements BluetoothNotification<byte[]> {
		private final String address;
		
		private final Map<String, String> profile;

		public NewRecordNotification(String address, Map<String, String> profile) {
			this.address = address;
			this.profile = profile;
		}
		
		@Override
		public void run(byte[] record) {
			lastRecord = record;
 			try {
				Protocol.NewRecordSignal newRecordSignal = new Protocol.NewRecordSignal(AGILE_NEW_RECORD_SIGNAL_PATH,
						lastRecord, address, profile);
				logger.info("Notifiying {}", this);
				connection.sendSignal(newRecordSignal);
			} catch (DBusException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 	Check the status of a device 
	 * @param deviceAddress
	 * @return
	 */
	@Override
	public StatusType DeviceStatus(String deviceAddress){
		try {
			if(((BluetoothDevice) bleManager.find(BluetoothType.DEVICE, null, deviceAddress, null)).getConnected()){
				return new StatusType(DeviceStatusType.CONNECTED.toString());
						}else{
				return new StatusType(DeviceStatusType.DISCONNECTED.toString());
			}
		} catch (Exception e) {
			logger.error("Error on checking device status {}", e.getMessage());
			}
		return new StatusType(DeviceStatusType.ERROR.toString());
	}
	

	// =========================UTILITY ==============

	public boolean isRemote() {
		return false;
	}
	
	void printDevice(BluetoothDevice device) {
		logger.info("Name = {}", device.getName());
		logger.info("Address = {}", device.getAddress());
		logger.info("Connected= {}", device.getConnected());
	}

	@Override
	public void finalize() {
		connection.disconnect();
	}

	/**
	 * Check if the device is newly discovered device
	 * 
	 * @param device
	 * @return
	 */
	private boolean isNewDevice(DeviceOverview device) {
		for (DeviceOverview dev : deviceList) {
			if (dev.getId().equals(device.getId())) {
				return false;
			}
		}
		return true;
	}

}
