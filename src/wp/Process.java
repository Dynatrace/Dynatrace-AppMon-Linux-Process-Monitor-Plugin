package wp;

import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;


public class Process {
		
		private String processBusinessName = null;
		private String processName = null;
		private String processArgs = null;
		private int processCountMinValue = 1; //Assume one instance of the Process is expected to be running. 
		private int processAvailability = 0;
		private int processAvailabilityViolation = 0;
		private int processInstanceCount = 0;
		private HashMap <Integer, Boolean> listenPorts = null;
		private boolean checkListenPorts = false;
		private int listenPortAvailabilityViolation = 0;
		
				
		public static final Logger log = LinuxProcessMonitor.log;
		
		public Process (String procBusinessName, String procName, String procArgs, int procCountMinVal) {
			processBusinessName = procBusinessName;			
			processName = procName;
			processArgs = procArgs;
			processCountMinValue = procCountMinVal;
			processAvailability = 0;
			processInstanceCount = 0;
			processAvailabilityViolation = 0;
			checkListenPorts = false;
						
		}
		
		public Process (String procBusinessName, String procName, String procArgs, int procCountMinVal, String listenPortsStr) {
			processBusinessName = procBusinessName;			
			processName = procName;
			processArgs = procArgs;
			processCountMinValue = procCountMinVal;
			processAvailability = 0;
			processInstanceCount = 0;
			processAvailabilityViolation = 0;
			if (listenPortsStr != null && listenPortsStr.trim().length() > 0) {
				checkListenPorts = true;
				
				String [] portNos = listenPortsStr.trim().split(",");
				listenPorts = new HashMap<Integer, Boolean> ();
				
				//log.info("Constructor: listenPortsStr: " + listenPortsStr + " Size : " + portNos.length);
				
				for (int n = 0; n < portNos.length; n++) {
					
					listenPorts.put(Integer.valueOf(portNos[n]), Boolean.valueOf(false));
					//log.info("Adding Port No:" + portNos[n] + " : " + Integer.valueOf(portNos[n]) + " for " + processBusinessName + " ListenPorts: " + 
							//listenPorts.toString());
				}
			}												
		}
		
		public void addProcessCount(String line) {
			
			if(line.contains(getProcessName()) && line.contains(getProcessArguments())) {
				processInstanceCount += 1;
				
				/* log.info("Found Proc Name: " + proc.getProcessName() + " AND Proc Args: " + 
				proc.getProcessArguments() + " Avail: " + proc.getProcessAvailability() + 
				" Violations: " + proc.getProcessAvailabilityViolationCount() + " Count: " + 
				proc.getProcessInstanceCount());*/
			
			}
				
			
		}
		
		
		public void setListenPortNo(String line) {
			//log.severe("SetListenPort:" + checkListenPorts + " : " + this.processBusinessName + ": " + line );
			if(checkListenPorts) {
				//log.severe("SetListenPort:" + this.processBusinessName + ": " + line);
				Set set = null;
				Iterator iter = null;
			
				set = listenPorts.entrySet();
				//log.severe("SetListenPort toStr: " + listenPorts.toString());
				iter = set.iterator();
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					//log.severe("SetListenPort2: " + line + " : " + (line.contains(":" + me.getKey())) + 
							//" : Proc Business Name: " + processBusinessName + " Trying Match for Port: " + me.getKey());
					if (line.contains(":" + me.getKey())) {
						//log.severe("SetListenPort2: " + processBusinessName + " found LIsten Port: " + me.getKey());
						listenPorts.put((Integer)me.getKey(), (new Boolean(true)));
						
					}
				}
			}
		}
		
		
		public int getProcessCountMinValue(){
			return processCountMinValue ;			
		}
		
		public int getProcessAvailability() {
			
			/**
			* Important logic to decide if Process is Available OR not. If no of instances found is more than Process Count Min Value, then the Process is considered Available (100%). 
			* Otherwise Process is considered not available (0 %).
			*/
			if (processInstanceCount >= processCountMinValue) {
				processAvailability = 100;
				processAvailabilityViolation = 0;
			} else {
				processAvailability = 0;
				processAvailabilityViolation = 1;
			}
			
			if (this.checkListenPorts) {
				Set set = null;
				Iterator iter = null;
				
				set = listenPorts.entrySet();
				iter = set.iterator();
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					
					Boolean val = (Boolean)me.getValue();
					if(!val.booleanValue()) {
						processAvailability = 0;
						processAvailabilityViolation = 1;
						listenPortAvailabilityViolation = 1;
					}
				}
				
			}
			
			return processAvailability;			
		}
		
		public int getProcessInstanceCount(){
			return processInstanceCount;			
		}
		
		public int getListenPortAvailabilityViolationCount(){
			return listenPortAvailabilityViolation;			
		}
		
		public ArrayList<Integer> getPortsNotListening() {
			
			if (listenPorts == null) {
				return null;
			}
			
			ArrayList<Integer> portsNotListening = new ArrayList<Integer>();
			
			Set set = null;
			Iterator iter = null;
			
			set = listenPorts.entrySet();
			iter = set.iterator();
			while(iter.hasNext()) {
				Map.Entry me = (Map.Entry)iter.next();
				
				Boolean val = (Boolean)me.getValue();
				//log.severe("In getPortsNotListening: " + this.processBusinessName + " portNo: " + me.getKey() + " Value: " + me.getValue());
				if(!val.booleanValue()) {
					portsNotListening.add((Integer)me.getKey());
				}
			}
			return portsNotListening;
		}
		
		public int getProcessAvailabilityViolationCount(){
			return processAvailabilityViolation;			
		}
		
		public String getProcessName() {
			return processName;
		}
		
		public String getProcessBusinessName() {
			return processBusinessName;
		}
		
		public String getProcessArguments() {
			return processArgs;
		}
				
	}