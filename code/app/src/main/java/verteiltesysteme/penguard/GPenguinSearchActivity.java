package verteiltesysteme.penguard;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import verteiltesysteme.penguard.guardianservice.GuardService;
import verteiltesysteme.penguard.guardianservice.Penguin;
import verteiltesysteme.penguard.guardianservice.TwoPhaseCommitCallback;


//here we search for bluetooth devices and the guard can pick a penguin to guard and then go on to the GGuardActivity

public class GPenguinSearchActivity extends PenguardActivity {
    static final int SCAN_PERIOD = 10000; // scan period in ms
    static final String EXTRA_DEVICE = "device"; //aka the penguin
    static final String EXTRA_Name = "newName";
    static final int REQUEST_ENABLE_BT = 1; // request code for bluetooth enabling
    static final int PERMISSION_REQUEST_FINE_LOCATION = 2; // request code for location permission
    static final int ASK_PENGUIN_NAME = 1;
    protected enum StoppedBy{
        NAMING, TIMEOUT, QUIT
    }

    ArrayList<BluetoothDevice> scanResultsList = new ArrayList<>();
    BroadcastReceiver bcr;
    ArrayAdapter<BluetoothDevice> scanResultsAdapter;
    ScanCallback scanCallback;
    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    Handler handler;
    Runnable scanStopperRunnable = new Runnable() {
        @Override
        public void run() {
            stopBluetoothScan(StoppedBy.TIMEOUT);
        }
    };
    Button restartScanButton;
    ScanSettings scanSettings;
    List<ScanFilter> scanFilters;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpenguin_search);

        // bluetooth LE scan settings
        scanSettings = new ScanSettings.Builder().build();

        //bind the service
        Intent intent = new Intent(this, GuardService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        handler = new Handler();
        restartScanButton = (Button) findViewById(R.id.restartScanButton);
        restartScanButton.setText(getText(R.string.scan));

        bcr = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    debug("Found a device");
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Add the name and address to an array adapter to show in a ListView
                    addDevice(device);
                }
            }
        };

        // Register the BroadcastReceiver
        IntentFilter intentfilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bcr, intentfilter); // Don't forget to unregister during onDestroy

        // bluetooth LE scan callback that is used for getting scan results
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                debug("Device found: " + result.getDevice().getName());
                if (result.getDevice() == null) return;
                addDevice(result.getDevice());
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult res : results) {
                    addDevice(res.getDevice());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                toast(getResources().getString(R.string.failBTScan));
                debug("Scan failed: " + errorCode);
            }
        };

        //initialize bluetooth adapter
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        //initialize list view for scan results
        ListView scanResults = (ListView) findViewById(R.id.scanResultlv);
        scanResultsAdapter = new BluetoothDevicesAdapter(this, R.layout.penguin_search_list, scanResultsList);
        scanResults.setAdapter(scanResultsAdapter);

        scanResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = (BluetoothDevice)parent.getItemAtPosition(position);
                Intent getPenguinName = new Intent(getApplicationContext(), GPenguinNameActivity.class);
                getPenguinName.putExtra(EXTRA_DEVICE, device);
                stopBluetoothScan(StoppedBy.NAMING);
                startActivityForResult(getPenguinName, ASK_PENGUIN_NAME);
            }
        });

        //request permission for fine location first if SDK is marshmallow or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
        }
        else { //lollipop or lower; permission request not needed, scan ahead
            turnOnBluetoothAndScan();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ASK_PENGUIN_NAME){
            if (resultCode == RESULT_OK){
                BluetoothDevice device = data.getParcelableExtra(EXTRA_DEVICE);
                String penguinName = data.getStringExtra(EXTRA_Name);
                TwoPhaseCommitCallback callback = new TwoPhaseCommitCallback() {
                    @Override
                    public void onCommit(String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                toast(getString(R.string.penguinAddSucceeded));
                            }
                        });
                    }
                    @Override
                    public void onAbort(String error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                toast(getString(R.string.penguinAddFailed));
                            }
                        });
                    }
                };
                Penguin penguin = new Penguin(device, penguinName, serviceConnection.getContext());
                penguin.registerSeenCallback(serviceConnection.getPenguinSeenCallback());
                serviceConnection.addPenguin(penguin, callback);
                Intent intent = new Intent(this, GGuardActivity.class);
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBluetoothScan(StoppedBy.QUIT);
        unregisterReceiver(bcr);
        if (serviceConnection != null && serviceConnection.isConnected()) {
            unbindService(serviceConnection);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                debug("Location permission granted");
                //We now have permission, so let's scan for bluetooth devices
                turnOnBluetoothAndScan();
            }
            else {
               toast(getString(R.string.permissionDeniedBTScan));
                restartScanButton.setEnabled(false);
            }
        }
    }

    private void turnOnBluetoothAndScan() {
        //test whether bluetooth is enabled, enable if not
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        }
        else {
            //bluetooth already on; scan ahead
            handler.postDelayed(scanStopperRunnable, SCAN_PERIOD);
            startBluetoothScan();
        }
    }

    private void startBluetoothScan() {
        restartScanButton.setEnabled(false); // So that user cannot press Scan while scan is running
        bluetoothAdapter.getBluetoothLeScanner().startScan(scanFilters,scanSettings,scanCallback);
        restartScanButton.setText(getText(R.string.scanningBTScan));
        bluetoothAdapter.startDiscovery();
        toast(getString(R.string.scanningBTScan));
        debug("Started scan");
    }

    private void stopBluetoothScan(StoppedBy stoppedBy) {
        debug("Stopped scan");
        //stop LE scan
        debug(bluetoothAdapter.getBluetoothLeScanner().toString());
        restartScanButton.setEnabled(true);
        restartScanButton.setText(getText(R.string.scan));
        if (scanResultsList.size() > 0) {
            switch(stoppedBy){
                case TIMEOUT:
                    toast(getString(R.string.stopBTScan));
                    break;
                case NAMING:
                    if (bluetoothAdapter.isDiscovering()){
                        toast(getString(R.string.stopBTScanNaming));
                    }
                    handler.removeCallbacks(scanStopperRunnable);
                    break;
                case QUIT:
                    handler.removeCallbacks(scanStopperRunnable);
                    break;
                default:
                    throw new IllegalArgumentException("No handler available for the enum passed as an argument!");
            }
        }
        else { // no results found
            toast(getString(R.string.noResultBTScan));
        }

        if (bluetoothAdapter.getBluetoothLeScanner() != null){
            bluetoothAdapter.getBluetoothLeScanner().stopScan(scanCallback);
            bluetoothAdapter.cancelDiscovery();
        }
    }

    public void scanButtonClicked (View view) {
        if (view == restartScanButton) {
            //"Restart Scan" button was clicked, flush list and start a scan
            scanResultsList.clear();
            scanResultsAdapter.notifyDataSetChanged();
            turnOnBluetoothAndScan();
        }
    }

    private void addDevice(BluetoothDevice device){
        if (!scanResultsList.contains(device)){
            scanResultsList.add(device);
            scanResultsAdapter.notifyDataSetChanged();
        }
    }
}
