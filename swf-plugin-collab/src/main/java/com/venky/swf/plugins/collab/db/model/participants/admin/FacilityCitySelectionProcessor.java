package com.venky.swf.plugins.collab.db.model.participants.admin;

import com.venky.swf.db.annotations.column.ui.OnLookupSelectionProcessor;

public class FacilityCitySelectionProcessor implements OnLookupSelectionProcessor<Facility>{

	public FacilityCitySelectionProcessor() {
	}

	public void process(String fieldSelected, Facility partiallyFilledModel) {
		if (fieldSelected.equals("CITY_ID")){
			partiallyFilledModel.setStateId(partiallyFilledModel.getCity().getStateId());
			partiallyFilledModel.setCountryId(partiallyFilledModel.getState().getCountryId());
		}
	}

}