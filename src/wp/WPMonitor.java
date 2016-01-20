package wp;

import java.util.Collection;

import com.dynatrace.diagnostics.sdk.MonitorMeasure30Impl;
import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.Status;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.PluginEnvironment;
import com.dynatrace.diagnostics.pdk.Status;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

public class WPMonitor extends LinuxProcessMonitor implements Monitor {

	private static final String METRIC_GROUP = "Linux Process Status Monitor";
	private static final String MSR_PROCESS_COUNT = "ProcessCount";
	private static final String MSR_PROCESS_AVAILABILITY = "ProcessAvailability";
	private static final String MSR_PROCESS_AVAILABILITY_VIOLATION = "Violation";
	private static final String MSR_LISTENPORT_AVAILABILITY_VIOLATION = "ListenPort_Violation";
	
	@Override
	public Status setup(MonitorEnvironment env) throws Exception {
		return super.setup(env);
	}

	@Override
	public Status execute(MonitorEnvironment env) throws Exception {
		Status result = super.execute(env);

		Collection<MonitorMeasure> measures;
		Set set = null;
		Iterator iter = null;
		
		
		if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_PROCESS_COUNT)) != null) {
			
			for (MonitorMeasure measure : measures) {
												
				set = processMap.entrySet();
				iter = set.iterator();
				
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					
					Process proc = (Process)me.getValue();
					/*log.severe("Dynamic Msr Proc Count: " + proc.getProcessBusinessName() + " Avail: " + proc.getProcessAvailability() + " Violations: " + 
							proc.getProcessAvailabilityViolationCount() + " Count Min Val: " + proc.getProcessCountMinValue());*/
										
					MonitorMeasure dynamicMeasure = env.createDynamicMeasure(measure, "Process", proc.getProcessBusinessName());
					dynamicMeasure.setValue(proc.getProcessInstanceCount());
										
				}				
			}			
		}
		
		if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_PROCESS_AVAILABILITY)) != null) {
			//log.severe("Found Proc Avail :" + measures.isEmpty() + " : Size " + measures.size());
			for (MonitorMeasure measure : measures) {
												
				set = processMap.entrySet();
				iter = set.iterator();
				
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					
					Process proc = (Process)me.getValue();
					
					/*log.severe("Dynamic Msr Avail: " + proc.getProcessBusinessName() + " Avail: " + proc.getProcessAvailability() + " Violations: " + 
							proc.getProcessAvailabilityViolationCount() + " Count Min Val: " + proc.getProcessCountMinValue());*/
					
					MonitorMeasure dynamicMeasure = env.createDynamicMeasure(measure, "Process", proc.getProcessBusinessName());
					
					dynamicMeasure.setValue(proc.getProcessAvailability());
				}				
			}
		}
		
		if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_PROCESS_AVAILABILITY_VIOLATION)) != null) {
			for (MonitorMeasure measure : measures) {
												
				set = processMap.entrySet();
				iter = set.iterator();
				
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					
					Process proc = (Process)me.getValue();
					/*log.severe("Dynamic Msr Violation: " + proc.getProcessBusinessName() + " Avail: " + proc.getProcessAvailability() + " Violations: " + 
							proc.getProcessAvailabilityViolationCount() + " Count Min Val: " + proc.getProcessCountMinValue());*/
										
					MonitorMeasure dynamicMeasure = env.createDynamicMeasure(measure, "Process", proc.getProcessBusinessName());
					dynamicMeasure.setValue(proc.getProcessAvailabilityViolationCount());
					
				}				
			}
		}
		
		
		
		if ((measures = env.getMonitorMeasures(METRIC_GROUP, MSR_LISTENPORT_AVAILABILITY_VIOLATION)) != null) {
			
			//log.severe("Found Process Count :" + MSR_LISTENPORT_AVAILABILITY_VIOLATION + ": " + measures.isEmpty() + " : Size " + measures.size());
			
			for (MonitorMeasure measure : measures) {
												
				
				set = processMap.entrySet();
				iter = set.iterator();
				
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					
					Process proc = (Process)me.getValue();
					measure.setValue(proc.getListenPortAvailabilityViolationCount());
										
					if (proc.getPortsNotListening() != null && proc.getPortsNotListening().size() > 0) {
						/*log.severe("Dynamic Msr Proc Count2: " + proc.getProcessBusinessName() + " Avail: " + proc.getProcessAvailability() + " Violations: " + 
								proc.getProcessAvailabilityViolationCount() + " Count Min Val: " + proc.getProcessCountMinValue() + 
								" Ports not Listening: " + proc.getPortsNotListening().size());*/
						
						ArrayList<Integer> list = proc.getPortsNotListening();
						if (list != null) {
							for(int i=0; i < list.size(); i++) {
								Integer portNo = (Integer)list.get(i);
								//log.severe("Adding Dyna Measure: " + portNo);// + " : " + portNo.getClass().getName()); 
								//MonitorMeasure dynamicMeasure = env.createDynamicMeasure(measure, "ListenPort", proc.getProcessBusinessName() + " - " + portNo.toString());
								MonitorMeasure dynamicMeasure = env.createDynamicMeasure(measure, proc.getProcessBusinessName(), portNo.toString());
								dynamicMeasure.setValue(1);
								
							}
						}
					}
				}				
			}			
		}
		
		return result;
	}

	@Override
	public void teardown(MonitorEnvironment env) throws Exception {
		super.teardown(env);
	}
	
	
}
