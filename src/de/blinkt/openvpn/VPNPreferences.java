package de.blinkt.openvpn;

import java.util.List;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.widget.Button;


public class VPNPreferences extends PreferenceActivity {

	private String mProfileUUID;
	private BasicSettings mBS;
	public void setmBS(BasicSettings mBS) {
		this.mBS = mBS;
	}

	public VPNPreferences() {
		super();
	
	}
	

	protected void onPause() {
		super.onPause();
		if(mBS!=null)
			mBS.savePreferences();

	}
	
	
	@Override
	protected void onStop() {
		super.onStop();
	};
	
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		//                                                          profileUUID
		mProfileUUID = getIntent().getStringExtra(getPackageName() + ".profileUUID");
		super.onCreate(savedInstanceState);

	
				
		if (hasHeaders()) {
			Button button = new Button(this);
			button.setText("Save");
			setListFooter(button);
		}
	}
	
	
	@Override
	public void onBuildHeaders(List<Header> target) {
		loadHeadersFromResource(R.xml.vpn_headers, target); 
		for (Header header : target) {
			if(header.fragmentArguments==null)
				header.fragmentArguments = new Bundle();
			header.fragmentArguments.putString(getPackageName() + ".profileUUID",mProfileUUID);
		}
	}
	
	@Override
	public void onBackPressed() {
		setResult(RESULT_OK, getIntent());
		super.onBackPressed();
	}
}

