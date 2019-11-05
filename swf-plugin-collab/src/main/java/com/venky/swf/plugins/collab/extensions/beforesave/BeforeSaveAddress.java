package com.venky.swf.plugins.collab.extensions.beforesave;

import com.venky.core.collections.SequenceMap;
import com.venky.core.collections.SequenceSet;
import com.venky.core.string.StringUtil;
import com.venky.core.util.ObjectUtil;
import com.venky.geo.GeoCoder;
import com.venky.geo.GeoLocation;
import com.venky.swf.db.extensions.BeforeModelSaveExtension;
import com.venky.swf.db.model.Model;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.collab.db.model.participants.admin.Address;
import com.venky.swf.routing.Config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BeforeSaveAddress<M extends Address & Model> extends BeforeModelSaveExtension<M>{


	protected String[] getAddressFields(){
		return Address.getAddressFields();
	}
    protected boolean isAddressVoid(M oAddress){
        return Address.isAddressVoid(oAddress);
    }
	protected boolean isAddressChanged(M oAddress){
		return Address.isAddressChanged(oAddress);
	}
	protected boolean isOkToSetLocationAsync(){
	    return true;
    }
	@Override
	public void beforeSave(M oAddress) {
		if (!isAddressChanged(oAddress) && !oAddress.getReflector().isVoid(oAddress.getLat()) && !oAddress.getReflector().isVoid(oAddress.getLng())) {
			return;
		}


		if (oAddress.getCityId() != null){
			oAddress.setStateId(oAddress.getCity().getStateId());
		}
		if (oAddress.getStateId() != null){
			oAddress.setCountryId(oAddress.getState().getCountryId());
		}
		if (!oAddress.getReflector().isVoid(oAddress.getLat()) && !oAddress.getReflector().isVoid(oAddress.getLng())){
			if (oAddress.getRawRecord().isFieldDirty("LAT") || oAddress.getRawRecord().isFieldDirty("LNG") ){
				//Set by user or LocationSetterTask
				return;
			}
		}
        if (!isAddressVoid(oAddress)){
            LocationSetterTask<M> setterTask = new LocationSetterTask<M>(oAddress);
            if (isOkToSetLocationAsync()){
                TaskManager.instance().executeAsync(setterTask,false);
            }else{
                setterTask.setLatLng(false);
            }
        }
	}

	public static class LocationSetterTask<M extends Address & Model> implements Task {
	    M oAddress = null;
        public LocationSetterTask(M address){
            this.oAddress = address;
        }

        private Set<String> getAddressQueries(M oAddress){
            SequenceMap<String,String> priorityFields = new SequenceMap<>();
            for (String f : Address.getAddressFields()) {
                if (!oAddress.getReflector().isVoid(oAddress.getReflector().get(oAddress,f))){
                    priorityFields.put(f, StringUtil.valueOf(oAddress.getReflector().get(oAddress,f)));
                }
            }

            StringBuilder defaultQueryString = new StringBuilder();
            if (priorityFields.containsKey("CITY_ID")) {
                defaultQueryString.append(oAddress.getCity().getName());
                defaultQueryString.append(" ").append(oAddress.getState().getName());
                defaultQueryString.append(" ").append(oAddress.getCountry().getName());
                priorityFields.remove("CITY_ID");
                priorityFields.remove("STATE_ID");
                priorityFields.remove("COUNTRY_ID");
            }
            if (priorityFields.containsKey("PIN_CODE_ID")){
                defaultQueryString.append(" ").append(oAddress.getPinCode().getPinCode());
                priorityFields.remove("PIN_CODE_ID");
            }

            SequenceSet<String> addressQueries = new SequenceSet<>();

            for (int i = priorityFields.size() -1 ; i >=0 ; i -- ){
                StringBuilder addressQuery = new StringBuilder();
                for (int j = 0 ; j <= i ; j ++) {
                    addressQuery.append(priorityFields.getValueAt(j)).append(" ");
                }
                addressQuery.append(defaultQueryString.toString());
                addressQueries.add(addressQuery.toString());
            }
            for (int i = priorityFields.size() -2 ; i >=0 ; i -- ){
                StringBuilder addressQuery = new StringBuilder();
                for (int j = 0 ; j <= i ; j ++) {
                    addressQuery.insert(0, " ");
                    addressQuery.insert(0, priorityFields.getValueAt(priorityFields.size()-1 - j));
                }
                addressQuery.append(defaultQueryString.toString());
                addressQueries.add(addressQuery.toString());
            }

            return addressQueries;
        }

        public void setLatLng(boolean persistAfterSetting){
            List<GeoCoder> coders = new ArrayList<GeoCoder>();
            coders.add(new GeoCoder("google"));
            coders.add(new GeoCoder("here"));
            coders.add(new GeoCoder("openstreetmap"));
            Map<String,String> params = new HashMap<>();
            params.put("here.app_id",Config.instance().getProperty("geocoder.here.app_id"));
            params.put("here.app_code",Config.instance().getProperty("geocoder.here.app_code"));
            params.put("google.api_key",Config.instance().getProperty("geocoder.google.api_key"));
            BigDecimal lat = null;
            BigDecimal lng = null;

            for (Iterator<GeoCoder> i = coders.iterator(); i.hasNext() && (ObjectUtil.isVoid(lat) || ObjectUtil.isVoid(lng)) ; ){
                GeoCoder coder = i.next();
                for (String address: getAddressQueries(oAddress)){
                    GeoLocation location = coder.getLocation(address,params);
                    if (location != null){
                        lat = location.getLat();
                        lng = location.getLng();
                        break ;
                    }
                }
            }
            if (lat != null && lng != null) {
                oAddress.setLat(lat);
                oAddress.setLng(lng);
                if ((oAddress.getRawRecord().isFieldDirty("LAT") || oAddress.getRawRecord().isFieldDirty("LNG")) && persistAfterSetting) {
                    oAddress.save();
                }
            }
        }
        @Override
        public void execute() {
            setLatLng(true);
        }
    }

}
