package agency.highlysuspect.installertoolsruntime.bastion;

import net.minecraftforge.installer.actions.ProgressCallback;

//simply styled after ProgressCallback.TO_STD_OUT
class GradleLogProgressCallback implements ProgressCallback {
	public GradleLogProgressCallback(LookingGlass glass) {
		this.glass = glass;
	}
	
	private final LookingGlass glass;
	private String currentStep;
	
	@Override
	public void message(String s, MessagePriority messagePriority) {
		if(messagePriority.compareTo(MessagePriority.HIGH) >= 0) {
			glass.lifecycle(s);
		} else {
			glass.info(s);
		}
	}
	
	@Override
	public void setCurrentStep(String s) {
		message(s, MessagePriority.HIGH);
		currentStep = s;
	}
	
	@Override
	public String getCurrentStep() {
		return currentStep;
	}
}
