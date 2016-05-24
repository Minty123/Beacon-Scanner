package com.hogervries.beaconscanner.adapter;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;

import com.hogervries.beaconscanner.R;
import com.hogervries.beaconscanner.fragment.BeaconFragment;
import com.hogervries.beaconscanner.fragment.BeaconLogFragment;

/**
 * Beacon Scanner, file created on 07/03/16.
 *
 * @author Boyd Hogerheijde
 * @author Mitchell de Vries
 */
public class TabPagerAdapter extends FragmentStatePagerAdapter {

    private final int NUM_OF_TABS = 2;

    private Context context;

    public TabPagerAdapter(FragmentManager fm, Context context) {
        super(fm);
        this.context = context;
    }

    @Override
    public Fragment getItem(int position) {
        switch (position) {
            case 0:
                return BeaconFragment.newInstance();
            case 1:
                return BeaconLogFragment.newInstance();
            default:
                return BeaconFragment.newInstance();
        }
    }

    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return context.getString(R.string.tab_details_title);
            case 1:
                return context.getString(R.string.tab_log_title);
            default:
                return context.getString(R.string.tab_details_title);
        }
    }

    @Override
    public int getCount() {
        return NUM_OF_TABS;
    }
}