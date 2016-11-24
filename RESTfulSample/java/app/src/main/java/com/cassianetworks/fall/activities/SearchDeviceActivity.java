package com.cassianetworks.fall.activities;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.cassianetworks.fall.BaseActivity;
import com.cassianetworks.fall.R;
import com.cassianetworks.fall.domain.Device;
import com.cassianetworks.fall.domain.DeviceHandle;
import com.cassianetworks.fall.views.MyListView;
import com.cassianetworks.sdklibrary.Callback;
import com.cassianetworks.sdklibrary.HttpUtils;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;

import org.xutils.common.util.LogUtil;
import org.xutils.view.annotation.ContentView;
import org.xutils.view.annotation.ViewInject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;

import okhttp3.Response;

import static com.cassianetworks.fall.BaseApplication.deviceManager;
import static com.cassianetworks.fall.BaseApplication.indicator;

@ContentView(R.layout.activity_search_device)
public class SearchDeviceActivity extends BaseActivity {
    @ViewInject(R.id.tv_page_name)
    TextView tvPageName;
    Device device;
    @ViewInject(R.id.lv_scan_result)
    MyListView lvScanResult;

    @Override
    protected void init() {

        tvPageName.setText(R.string.search_device_result);
        final ScanResultAdapter adapter = new ScanResultAdapter();
        lvScanResult.setAdapter(adapter);
        deviceManager.clearScanDevList();
        indicator.scan(10000, new HttpUtils.OkHttpCallback() {
            @Override
            protected void onSuccess(final Response response) {

                Reader charStream = response.body().charStream();
                BufferedReader in = new BufferedReader(charStream);
                String line;

                try {
                    while ((line = in.readLine()) != null) {
                        if (line.contains("CassiaFD_1.2") || TextUtils.isEmpty("CassiaFD_1.2")) {

                            HashMap result = new Gson().fromJson(line.split("data:")[1], HashMap.class);
                            LinkedTreeMap bdaddrs = (LinkedTreeMap) ((ArrayList) result.get("bdaddrs")).get(0);
                            final String bdaddr = (String) bdaddrs.get("bdaddr");
                            final String scanData = (String) result.get("scanData");
                            final String name = (String) result.get("name");
                            final double rssi = (double) result.get("rssi");
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    device = new Device(name, bdaddr, rssi, scanData);
                                    deviceManager.addScanDevice(device);
                                    adapter.notifyDataSetChanged();
                                }
                            });
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            @Override
            protected void onFailure(String msg) {

            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        indicator.stopScan();
    }

    private class ScanResultAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return deviceManager.getScanDevList().size();
        }

        @Override
        public Device getItem(int position) {
            return deviceManager.getScanDevList().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }


        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {

            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if (convertView == null)
                convertView = inflater.inflate(R.layout.item_lv_search_device_list, parent, false);
            final TextView tvDeviceName = (TextView) convertView.findViewById(R.id.tv_name);
            final TextView tvDeviceMac = (TextView) convertView.findViewById(R.id.tv_mac);
            final TextView tvDeviceRssi = (TextView) convertView.findViewById(R.id.tv_rssi);
            final View ivAdd = convertView.findViewById(R.id.iv_add);
            final Device device = getItem(position);
            final String mac = device.getBdaddr();
            tvDeviceName.setText(device.getName());
            tvDeviceMac.setText(mac);
            tvDeviceRssi.setText(device.getRssi() + "");
            ivAdd.setSelected(false);

            ivAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    indicator.connect(mac, new Callback<Integer>() {
                        @Override
                        public void run(Integer value) {
                            LogUtil.d("test search connect value" + value);
                            if (value == 1) {

                                indicator.discoverServices(mac, new Callback<String>() {
                                    @Override
                                    public void run(String value) {
                                        if (!TextUtils.isEmpty(value)) {

                                            LogUtil.d("test discover service data " + value);
                                            HashMap ret = new Gson().fromJson(value, HashMap.class);
                                            ArrayList services = (ArrayList) ret.get("services");
                                            for (int i = 0; i < services.size(); i++) {
                                                LinkedTreeMap<String, Object> map = (LinkedTreeMap<String, Object>) services.get(i);
                                                int startHandle = ((Double) map.get("startHandle")).intValue();
                                                int endHandle = ((Double) map.get("endHandle")).intValue();
                                                String uuid = (String) map.get("uuid");

                                                ArrayList characteristics = (ArrayList) map.get("characteristics");
                                                if (characteristics != null)
                                                    for (int j = 0; j < characteristics.size(); j++) {
                                                        LinkedTreeMap<String, Object> c = (LinkedTreeMap<String, Object>) characteristics.get(j);
                                                        int handle = ((Double) c.get("handle")).intValue();
                                                        String cuuid = (String) c.get("uuid");
                                                        int properties = ((Double) c.get("properties")).intValue();
                                                        int valueHandle = ((Double) c.get("valueHandle")).intValue();
                                                        device.getHandleList().add(new DeviceHandle(handle, cuuid,properties,valueHandle));
                                                    }

                                            }
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    deviceManager.refreshDevicesList(true, device, new Callback<Integer>() {
                                                        @Override
                                                        public void run(Integer value) {
                                                        }
                                                    });
                                                }
                                            });

                                        } else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    ivAdd.setSelected(!ivAdd.isSelected());
                                                }
                                            });

                                        }

                                    }
                                });
                            } else {
                                LogUtil.d("test connect device fail value" + value);
                            }
                        }
                    });

                    ivAdd.setSelected(!ivAdd.isSelected());
                }
            });
            return convertView;
        }


    }

}
