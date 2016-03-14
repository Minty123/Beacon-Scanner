package com.hogervries.beaconscanner.fragment;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.afollestad.assent.Assent;
import com.afollestad.assent.AssentCallback;
import com.afollestad.assent.PermissionResultSet;
import com.hogervries.beaconscanner.BeaconStore;
import com.hogervries.beaconscanner.R;
import com.hogervries.beaconscanner.activity.SettingsActivity;
import com.hogervries.beaconscanner.adapter.BeaconAdapter;
import com.hogervries.beaconscanner.adapter.BeaconAdapter.OnBeaconSelectedListener;
import com.hogervries.beaconscanner.domain.BeaconFormat;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.BeaconTransmitter;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import butterknife.Bind;
import butterknife.BindColor;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Beacon Scanner, file created on 10/03/16.
 * <p/>
 * This fragment scans for beacons and transmits as a beacon.
 * If there are beacons in the area a list will be displayed.
 *
 * @author Boyd Hogerheijde
 * @author Mitchell de Vries
 */
public class ScanTransmitFragment extends Fragment implements BeaconConsumer {

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final String REGION_ID = "Beacon_scanner_region";

    @Bind(R.id.toolbar) Toolbar mToolbar;
    @Bind(R.id.toolbar_title) TextView mToolbarTitle;
    @Bind(R.id.scan_circle) ImageView mScanCircleView;
    @Bind(R.id.start_scan_button) ImageButton mStartButton;
    @Bind(R.id.stop_scan_button) ImageButton mStopButton;
    @Bind(R.id.pulse_ring) ImageView mPulsingRing;
    @Bind(R.id.scan_transmit_switch) Switch mScanTransmitSwitch;
    @Bind(R.id.scan_switch_button) Button mScanSwitchButton;
    @Bind(R.id.transmit_switch_button) Button mTransmitSwitchButton;
    @Bind(R.id.slide_layout) FrameLayout mSlideLayout;
    @Bind(R.id.beacon_recycler_view) RecyclerView mBeaconRecyclerView;
    @BindColor(R.color.colorWhite) int mWhite;
    @BindColor(R.color.colorGrey) int mGrey;

    private boolean mModeIsTransmitting;
    private boolean mIsScanning;
    private boolean mIsTransmitting;

    private MenuItem mStopScanItem;
    private BeaconManager mBeaconManager;
    private BeaconTransmitter mBeaconTransmitter;
    private BeaconAdapter mBeaconAdapter;
    private OnBeaconSelectedListener mCallback;
    private BeaconStore mBeaconStore;

    /**
     * Creates a new instance of this fragment.
     *
     * @return ScanTransmitFragment.
     */
    public static ScanTransmitFragment newInstance() {
        return new ScanTransmitFragment();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mCallback = (OnBeaconSelectedListener) context;
        } catch (ClassCastException notImplementedException) {
            throw new ClassCastException(context.toString()
                    + " must implement OnBeaconSelectedListener");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View beaconListView = inflater.inflate(R.layout.fragment_beacon_list, container, false);
        // Binding resources to this fragments view.
        ButterKnife.bind(this, beaconListView);
        // Setting toolbar.
        setToolbar();
        // Getting instance of beacon store.
        mBeaconStore = BeaconStore.getInstance();
        // Setting up BeaconManager.
        setUpBeaconManager();
        // Setting linear layout manager as layout manager for the beacon recycler view.
        mBeaconRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        // Updates user interface so that all the right views are displayed.
        updateUI();

        return beaconListView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_beacon_list, menu);
        // Initializing item as member so changes to its properties can be done within other methods.
        mStopScanItem = menu.findItem(R.id.stop_scanning);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.stop_scanning:
                // Clears list of beacons and stops scanning.
                mBeaconStore.clearBeacons();
                stopScanning();
                updateUI();
                mStopScanItem.setVisible(false);
                return true;
            case R.id.settings:
                startActivity(new Intent(getActivity(), SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Sets custom transparent toolbar as action bar.
     */
    private void setToolbar() {
        ((AppCompatActivity) getActivity()).setSupportActionBar(mToolbar);
    }

    /**
     * Initializes beacon manager and sets all needed properties.
     */
    private void setUpBeaconManager() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mBeaconManager = BeaconManager.getInstanceForApplication(getActivity());
        // Sets beacon layouts so that the app knows for what type of beacons to look.
        setBeaconLayouts();
        // Sets scanning periods.
        mBeaconManager.setForegroundScanPeriod(Integer.parseInt(preferences.getString("key_scan_period", "1100")));
        // Sets between scanning periods.
        mBeaconManager.setForegroundBetweenScanPeriod(Integer.parseInt(preferences.getString("key_between_scan_period", "0")));
        // Initializing cache and setting tracking age.
        BeaconManager.setUseTrackingCache(true);
        mBeaconManager.setMaxTrackingAge(Integer.parseInt(preferences.getString("key_tracking_age", "5000")));
    }

    /**
     * Setting beacon layouts so that the beacon manager knows what type of beacon to scan for.
     */
    private void setBeaconLayouts() {
        mBeaconManager.getBeaconParsers().clear();
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconFormat.APPLE_BEACON.getFormat()));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconFormat.SAMSUNG_BEACON.getFormat()));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_TLM_LAYOUT));
        mBeaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT));
    }

    /**
     * Updates user interface to display views based on current state of data.
     */
    private void updateUI() {
        List<Beacon> beacons = mBeaconStore.getBeacons();
        if (mBeaconAdapter == null) {
            // Initializing BeaconAdapter.
            mBeaconAdapter = new BeaconAdapter(beacons, mCallback, getActivity());
            mBeaconRecyclerView.setAdapter(mBeaconAdapter);
        } else {
            mBeaconAdapter.setBeacons(beacons);
            mBeaconAdapter.notifyDataSetChanged();
        }
        setSlideLayout(beacons);
    }

    /**
     * Sets sliding layout if needed.
     *
     * @param beacons List of beacons.
     */
    private void setSlideLayout(List<Beacon> beacons) {
        if (!beacons.isEmpty() && mSlideLayout.getVisibility() == View.INVISIBLE) {
            // Animates sliding up.
            slideUpBeaconList();
            mStopScanItem.setVisible(true);
        } else if (beacons.isEmpty() && mSlideLayout.getVisibility() == View.VISIBLE) {
            // Animates sliding down.
            slideDownBeaconList();
            mStopScanItem.setVisible(false);
        }
    }

    /**
     * Starts sliding up animation.
     */
    private void slideUpBeaconList() {
        mSlideLayout.setVisibility(View.VISIBLE);
        mSlideLayout.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.anim_slide_up));
    }

    /**
     * Starts sliding down animation.
     */
    private void slideDownBeaconList() {
        mSlideLayout.setVisibility(View.INVISIBLE);
        mSlideLayout.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.anim_slide_down));
    }

    @OnClick({R.id.start_scan_button, R.id.stop_scan_button, R.id.scan_circle})
    void onScanButtonClick() {
        if (!Assent.isPermissionGranted(Assent.ACCESS_COARSE_LOCATION)) {
            requestLocationPermission();
        } else {
            if (!mModeIsTransmitting) toggleScanning();
            else toggleTransmitting();
        }
    }

    private void toggleScanning() {
        if (!mIsScanning) startScanning();
        else stopScanning();
    }

    /**
     * Starts scanning for beacons.
     */
    private void startScanning() {
        if (!mBeaconManager.checkAvailability()) {
            requestBluetooth();
        } else {
            mIsScanning = true;
            // Beacon manager binds the beacon consumer and starts service.
            mBeaconManager.bind(this);
            startAnimation();
        }
    }

    /**
     * Stops scanning for beacons.
     */
    private void stopScanning() {
        mIsScanning = false;
        // Beacon manager unbinds the beacon consumer and stops service.
        mBeaconManager.unbind(this);
        stopAnimation();
    }

    private void toggleTransmitting() {
        if (!mIsTransmitting) startTransmitting();
        else stopTransmitting();
    }

    private void startTransmitting() {
        if (!mBeaconManager.checkAvailability()) {
            requestBluetooth();
        } else {
            if (!(BeaconTransmitter.checkTransmissionSupported(getActivity()) == BeaconTransmitter.SUPPORTED)) {
                notifyTransmittingNotSupported();
            } else {
                mIsTransmitting = true;
                Beacon beacon = new Beacon.Builder()
                        .setId1("2f234454-cf6d-4a0f-adf2-f4911ba9ffa6")
                        .setId2("1")
                        .setId3("2")
                        .setManufacturer(0x0118)
                        .setTxPower(-59)
                        .setDataFields(Arrays.asList(new Long[]{0l}))
                        .build();
                BeaconParser beaconParser = new BeaconParser().setBeaconLayout(BeaconFormat.APPLE_BEACON.getFormat());
                mBeaconTransmitter = new BeaconTransmitter(getActivity(), beaconParser);
                mBeaconTransmitter.startAdvertising(beacon);
                startAnimation();
            }
        }
    }

    private void stopTransmitting() {
        mIsTransmitting = false;
        mBeaconTransmitter.stopAdvertising();
        stopAnimation();
    }

    /**
     * Animates scan button to indicate that it's scanning.
     */
    private void startAnimation() {
        mScanCircleView.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.anim_zoom_in));
        mStartButton.setImageResource(R.drawable.ic_circle);
        mStopButton.setVisibility(View.VISIBLE);
        pulseAnimation();
    }

    /**
     * Animates stopping of scanning.
     */
    private void stopAnimation() {
        mScanCircleView.startAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.anim_zoom_out));
        mStartButton.setImageResource(mModeIsTransmitting ? R.drawable.ic_button_transmit : R.drawable.ic_button_scan);
        mStopButton.setVisibility(View.INVISIBLE);
        mPulsingRing.clearAnimation();
    }

    /**
     * Animates pulsing of button to indicate scanning.
     */
    private void pulseAnimation() {
        AnimationSet set = new AnimationSet(false);
        set.addAnimation(AnimationUtils.loadAnimation(getActivity(), R.anim.anim_pulse));
        mPulsingRing.startAnimation(set);
    }

    @OnClick(R.id.scan_switch_button)
    void switchToScanning() {
        setToolbarTitle(R.string.beacon_scanner);
        mModeIsTransmitting = false;
        mScanTransmitSwitch.setChecked(false);
        mScanSwitchButton.setTextColor(mWhite);
        mTransmitSwitchButton.setTextColor(mGrey);
        mStartButton.setImageResource(R.drawable.ic_button_scan);
    }

    @OnClick(R.id.transmit_switch_button)
    void switchToTransmitting() {
        setToolbarTitle(R.string.beacon_transmitter);
        mModeIsTransmitting = true;
        mScanTransmitSwitch.setChecked(true);
        mScanSwitchButton.setTextColor(mGrey);
        mTransmitSwitchButton.setTextColor(mWhite);
        mStartButton.setImageResource(R.drawable.ic_button_transmit);
    }

    private void setToolbarTitle(@StringRes int title) {
        mToolbarTitle.setText(title);
    }

    @Override
    public void onBeaconServiceConnect() {
        mBeaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> collection, Region region) {
                // Updates list of beacons.
                mBeaconStore.updateBeacons(collection);
                // If this fragment is added to its parent activity it will update the UI.
                if (isAdded()) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // If the app is scanning then update the UI.
                            // This prevents showing a list when scanning has already stopped.
                            if (mIsScanning) updateUI();
                        }
                    });
                }
            }
        });

        try {
            // Starting ranging of beacons in our defined region.
            mBeaconManager.startRangingBeaconsInRegion(new Region(REGION_ID, null, null, null));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Context getApplicationContext() {
        return getActivity().getApplicationContext();
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
        getActivity().unbindService(serviceConnection);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        return getActivity().bindService(intent, serviceConnection, i);
    }

    /**
     * Android M and up: requests permission for location(BLE needs it).
     */
    private void requestLocationPermission() {
        Assent.requestPermissions(new AssentCallback() {
            @Override
            public void onPermissionResult(PermissionResultSet permissionResultSet) {
                // Intentionally left blank
            }
        }, PERMISSION_REQUEST_COARSE_LOCATION, Assent.ACCESS_COARSE_LOCATION);
    }

    /**
     * Requests that the user turns on bluetooth.
     */
    private void requestBluetooth() {
        new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.bluetooth_not_enabled))
                .setMessage(getString(R.string.please_enable_bluetooth))
                .setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Initializing intent to go to bluetooth settings.
                        Intent bltSettingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                        startActivity(bltSettingsIntent);
                    }
                })
                .show();
    }

    private void notifyTransmittingNotSupported() {
        new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.transmitting_not_supported))
                .setMessage(getString(R.string.transmitting_not_supported_message))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
        mBeaconManager.unbind(this);
        // Deleting instance of singleton.
        BeaconStore.deleteInstance();
    }
}