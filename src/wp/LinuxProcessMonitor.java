package wp;

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
import java.util.logging.Logger;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

public class LinuxProcessMonitor {

	private static final String CONFIG_PROCESS_OWNER_USER = "processOwnerUser";
	private static final String CONFIG_SERVER_USERNAME = "serverUsername";
	private static final String CONFIG_SERVER_PASSWORD = "serverPassword";
	private static final String CONFIG_METHOD = "method";
	private static final String CONFIG_AUTH_METHOD="authMethod";
	private static final String CONFIG_PASSPHRASE = "publickeyPassphrase";
	
	//Constants required for extracting properties from User input to plug-in
	private static final String CONFIG_PROCESS_PREFIX = "process";
	private static final String CONFIG_BUSINESS_NAME = "_businessname";
	private static final String CONFIG_PROCESS_NAME = "_processname";
	private static final String CONFIG_PROCESS_ARGUMENTS = "_processarguments";
	private static final String CONFIG_PROCESS_MIN_COUNT = "_countminvalue";
	
	private boolean matchRuleSuccess;
	
	private String processOwnerUsername = "";
	private String serverName = "";
	private String serverUsername = "";
	private String serverPassword = null;
	private String method = null;
	private String authMethod = null;
	
	private boolean executePortListenCheck = false; //Flag to indicate that one or more processes needs its Listen Port No's to be verified. 

	
	public static final Logger log = Logger.getLogger(LinuxProcessMonitor.class.getName());
	
	HashMap <String, Process> processMap = new HashMap<String, Process>();
	
	
	
	protected Status setup(PluginEnvironment env) throws Exception {
		//log.severe("Entering setup");
		Status result = new Status(Status.StatusCode.Success);
        
		serverName = env.getHost().getAddress();        
		processOwnerUsername = env.getConfigString(CONFIG_PROCESS_OWNER_USER);		
		method = env.getConfigString(CONFIG_METHOD) == null ? "SSH" : env.getConfigString(CONFIG_METHOD).toUpperCase();
		authMethod = env.getConfigString(CONFIG_AUTH_METHOD) == null ? "PASSWORD" : env.getConfigString(CONFIG_AUTH_METHOD).toUpperCase();
		
		serverUsername = env.getConfigString(CONFIG_SERVER_USERNAME);	       
		serverPassword = (authMethod.equals("PUBLICKEY")) ? env.getConfigPassword(CONFIG_PASSPHRASE) : env.getConfigPassword(CONFIG_SERVER_PASSWORD);
		
		for (int configCount = 1; configCount < 20; configCount++) {
			String processProps = env.getConfigString(CONFIG_PROCESS_PREFIX + "-" + configCount);
			
			if (processProps != null && processProps.trim().length() > 0) {
				
				String props[] = processProps.split(";");
				
				//log.info("After split:" + props[0] + " : " + props[1] + " : " + props[2] + " : " + props[3]);
				
				String procBusinessName = props[0].trim();
				String procName = props[1].trim();
				String procArgs = props[2].trim();
				String strProcCountMinValue = props[3];
				
				Process proc = null;
				
				if(props.length == 5) {
					String strListenPortNos = props[4];
					proc = new Process(procBusinessName, procName, procArgs, Integer.parseInt(strProcCountMinValue.trim()), strListenPortNos);
					executePortListenCheck = true; //Set this flag to True even if one Process needs to check its Listen Port No's.
				} else {
					proc = new Process(procBusinessName, procName, procArgs, Integer.parseInt(strProcCountMinValue.trim())); 
				}
								
				processMap.put(procBusinessName, proc);
				
				/*log.severe("Adding Process:" + proc.getProcessBusinessName() + " : " + proc.getProcessName() + " : " + proc.getProcessArguments() + 
						" : Count Min Value: " + proc.getProcessCountMinValue());*/
			}
			
		}
		
		return result;
	}

	protected Status execute(PluginEnvironment env) throws Exception {
		Status result = new Status(Status.StatusCode.Success);

		executeProcessCheck(result);

		matchRuleSuccess = false;
		
		result.setMessage("to be added");
		matchRuleSuccess = true;

		return result;
		
	}
	
	protected boolean isMatchRuleSuccess() {
		return matchRuleSuccess;
	}

	
	
	protected void teardown(PluginEnvironment env) throws Exception {
		
	}

	/**
	 * 
	 * @param status
	 */
	private void executeProcessCheck(Status status) {
		
		//log.severe("In executeProcessCheck");
		String command = null;
		
		/**
		 * Incase process user is mentioned, then use command 'ps -efu' and specify the process username. Otherwise use the command 'ps -ef'. 
		 */	
		if (processOwnerUsername != null && processOwnerUsername.trim().length() > 0) {
			command = "ps -efu " + processOwnerUsername + " | grep -v 'grep'";
		} else {
			
			command = "ps -ef | grep -v 'grep'";
		}
		
		String inputstream = "";
		String line = null;
		boolean isAuthenticated = false;
		
		Connection conn = null;
		Session sess = null;
		Set set = null;
		Iterator iter = null;
		
		try {
			
			conn = new Connection(serverName);
			conn.connect();
			isAuthenticated = conn.authenticateWithPassword(serverUsername, serverPassword);
			
			if(!isAuthenticated) {
				status.setStatusCode(Status.StatusCode.ErrorInternalUnauthorized);
				log.severe("Authentication Failed: server: " + serverName + " : Server Username: " + serverUsername);
				return;
			}
			
			sess = conn.openSession();
			sess.execCommand(command);
			
			BufferedReader br = new BufferedReader(new InputStreamReader(sess.getStdout()));
			
			while ((line = br.readLine()) != null) {
								
				set = processMap.entrySet();
				iter = set.iterator();
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					Process proc = (Process)me.getValue();
					proc.addProcessCount(line);
				}
			}	
			
			sess.close();
			
			executePortListenCheck(conn, sess, status);
			
			
			conn.close();
			
		} catch (IOException e) {			
			e.printStackTrace(System.err);			
			log.severe("IOException: in executeProcessCheck: " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace(System.err);
			log.severe("Exception: in executeProcessCheck: " + e.getMessage());
		}
		
		try { if (sess != null) sess.close(); } catch (Exception ex) {}
		try { if (conn != null) conn.close(); } catch (Exception ex) {}
				
	}
	
	/**
	 * Work in progress.
	 * @param conn
	 * @param sess
	 * @param status
	 */
	
	private void executePortListenCheck(Connection conn, Session sess, Status status) {
		
		String command = "netstat -ntl";
		
		String inputstream = "";
		String line = null;
		boolean isAuthenticated = false;
		
		Set set = null;
		Iterator iter = null;
		
		try {
			
			if (executePortListenCheck) {
				sess = conn.openSession();
				sess.execCommand(command);
				BufferedReader br = new BufferedReader(new InputStreamReader(sess.getStdout()));
				
				while ((line = br.readLine()) != null) {
									
					set = processMap.entrySet();
					iter = set.iterator();
					while(iter.hasNext()) {
						Map.Entry me = (Map.Entry)iter.next();					
						Process proc = (Process)me.getValue();					
						proc.setListenPortNo(line);
					}
				}	
			}
			
			sess.close();
			conn.close();
			
		} catch (IOException e) {			
			e.printStackTrace(System.err);			
			log.severe("IOException in executePortListenCheck: " + e.getMessage());
		} catch (Exception e) {
			e.printStackTrace(System.err);
			log.severe("Exception in executePortListenCheck: " + e.getMessage());
		}
		
		try { if (sess != null) sess.close(); } catch (Exception ex) {}
		try { if (conn != null) conn.close(); } catch (Exception ex) {}
				
	}
	
	
	/**
	 * Added for testing the logic of process availability check along with process name and process agrument values.
	 * This is a copy-paste of the logic in LinuxProcessMonitor.executeCommand() method
	 */
	public static void main (String srgs[]) {
						
		String hostname2 = "192.168.131.138";
		String inputstream = "";
		String line = "";
		String systemUsername = "root"; 
		String processUserName = "nobody";
		String pw = "greenmouse";
		String processName = "/usr/";
		String processArgs = "/adlex";
		
		//String command = "ps -ef " + " | grep -s " + processName + " | grep -s " + processArgs + " | grep -v 'grep' |  wc -l";
		String command = "ps -ef " + " | grep -v 'grep'";
		
		HashMap <String, Process> processMap = new HashMap<String, Process>();
		
		
		Process p1 = new Process("p1", "/usr/bin/stunnel", "/usr/adlex", 2);
		
		Process p2 = new Process("p2", "/sbin/mingetty", "/dev", 13);
		Process p3 = new Process("p3", "/usr/bin/perl", "/adlex", 12);
		Process p4 = new Process("p4", "/bin/sh", "/usr/adlex/rtm/bin", 20);
		
		
		processMap.put("p1", p1);
		processMap.put("p2", p2);
		processMap.put("p3", p3);
		processMap.put("p4", p4);
		
		boolean isAuthenticated = false;
		
		try {
			Connection conn = new Connection(hostname2);
			conn.connect();
			isAuthenticated = conn.authenticateWithPassword(systemUsername, pw);

			Session sess = conn.openSession();
						
			sess.execCommand(command);

			BufferedReader br = new BufferedReader(new InputStreamReader(sess.getStdout()));

			Set set = null;
			Iterator iter = null;
			
			//line = br.readLine();
			int n = 0;
			while ((line = br.readLine()) != null) {
				n++;
				
				set = processMap.entrySet();
				iter = set.iterator();
				while(iter.hasNext()) {
					Map.Entry me = (Map.Entry)iter.next();
					
					Process proc = (Process)me.getValue();
					proc.addProcessCount(line);
				}
			}
			
			set = processMap.entrySet();
			iter = set.iterator();
			
			/*
			while(iter.hasNext()) {
				Map.Entry<String, Process> me = (Map.Entry)iter.next(); 
					
				Process proc = (Process)me.getValue();
				System.out.println(me.getKey() + " Avail: " + proc.getProcessAvailability() + " Violation: " + proc.getProcessAvailabilityViolationCount());
				
			}*/
			
			sess.execCommand(command);
			
			sess.close();
			conn.close();		
			
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.err.println("Caught IOException: " + e.getMessage()  + " Hostname: " + hostname2 + " isAuthenticated: " + isAuthenticated + " Username: " + systemUsername + " Process: " + processName + 
			" Process Args: " + processArgs + " Command = " + command);
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.err.println("Caught Exception: " + e.getMessage() + " Hostname: " + hostname2 + " isAuthenticated: " + isAuthenticated + " Username: " + systemUsername + " Process: " + processName + 
			" Process Args: " + processArgs + " Command = " + command);
		}				
	}	
}
