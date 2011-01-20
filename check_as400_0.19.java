/*----------------------------------
Nagios Plugin to check an IBM AS/400

Developed June 2003
Last Modified September 23 2004
------------------------------------

TODO:

*Move .as400 to etc/nas400/.as400 

*Fix dspmsg to tell howmany unanswered messages

*Add feature to queue monitoring to check status of attached writer (RLS/HLD)

*Add check dsk sts

------------------------------------

CHANGE LOG:

0.11
*Added checks for errors on login, and for invalid queue name

0.12a
*Fixed tight loop eating all cpu time when not recieving input
*Several spots of code cleanups. Mainly places where I wasn't checking the 
 return values of functions before continuing.

0.13
* Logout instead of just disconnecting.

0.14
*Added check for expired password
*Corrected several incorrect exit status commands
*Added check for expiring password

0.15
*Minor changes to login procedure (to make it more compatible with other
 versions of OS/400)
*Added check for break messages on login
*Added check for command not allowed error

0.15a
*Minor changes to login procedure (again to make it more compatible with
 other versions of OS/400)

0.16
*Asthetic change to the usage output
*Added ability to check if subsystem is running
*Added ability to check number of active jobs in system
*Added ability to check if a job is running
*Added debug functionality (to help you and me solve problems!)
*Modified check for invalid user name
*Modified check for invalid password
*Removed the transmission of 'esc'(asc 27) at beginning of connection

0.17
*Minor code cleanups
*Added check to see if a login completes.  Use it in dependency definitions
 for about every other service you monitor. If the login fails, it obviously
 cant check anything. All your checks will return error unable to login.
 This will prevent one from getting a zillion alerts in the middle of the night.
 (Not that I would know anything about that!)
*Fixed problem with active jobs not retrieving properly(parse problem).
*Fixed wrksyssts and dspmsg to execute with proper assistance level and
 modified parsing accordingly.
*Modified the way the variables CPU,DB,US,JOBS,SBS,AJ,CJ are parsed.
*Changed the way the "...is allocated..." message is found during logon.
*Added a check on the double conversion for cases where a ',' is used
 instead of a '.' 
*Modified parsing on CJ variable when job not found

0.18
*Changed the name of the command vars class(CmdVars.java) to
 check_as400_cmd_vars.java for standardization
*Created LANG class and substituted on language depended strings
 with constants from LANG class
*Created language definitions for GERMAN

0.19
-----------
*spanish Translation for Status 'Active' added
*Termination Token '[0m' in Method 'logout (';53H' only sent in standard SignOn-Screen)
*Implementation of Variable CJS = "Check for Job Status"


*/



import java.io.*;
import java.net.*;
import java.text.*;

public class check_as400{
	final static String VERSION="0.18";

	public static void printUsage(){
		System.out.println("Usage: check_as400 -H host -u user -p pass [-v var] [-w warn] [-c critical]\n");
		System.out.println("    (-h) for detailed help");
		System.out.println("    (-V) for version information\n");
	}
	
	public static void printUsageDetail(){
		System.out.println("Usage: check_as400 -H host -u user -p pass [-v var] [-w warn] [-c critical]\n");
		System.out.println("Options:\n");
		System.out.println("-H HOST\n   Name of the host to check");
		System.out.println("-u USERNAME\n   Username to login to host");
		System.out.println("-p PASSWORD\n   Password to login to host");
		System.out.println("-v STRING\n   Variable to check.  Valid variables include:");
		System.out.println("      AJ                = number of active jobs in system");
		System.out.println("      CJ <job>          = check to see if job <job> is in the system");
		System.out.println("      CJS <sbs> <job> [status <status>] [noperm]");
		System.out.println("                        = check to see if job is existing in Subsystem and has this status.");
		System.out.println("                          Job checking can be controlled by :");
		System.out.println("                          status <status> 	= any other status goes to critical");
	  //System.out.println("                          cstatus <status> 	= only this status goes to critical");
	  //System.out.println("                          wstatus <status> 	= only this status goes to warning");
		System.out.println("                          noperm   		= don't go to critical if job is not in the system");
	  //System.out.println("                          onlyone  		= go to critical if job is shown twice otherwise ignore it");
		System.out.println("                          REMARK : if JobStatus is set, it has highest Priority");
		System.out.println("      CPU               = CPU load");
		System.out.println("      DB                = DB utilization");
		System.out.println("      JOBS              = number of jobs in system");
		System.out.println("      LOGIN             = check if login completes");
		System.out.println("      MSG <user>        = check for any unanswered messages on msg queue <user>");
		System.out.println("                          Any unanswered messages causes warning status.");
		System.out.println("      OUTQ <queue>      = check outq files, writer and status. No writer, or");
		System.out.println("                          status of 'HLD' causes warning status. This default");
		System.out.println("                          behavior can be modified with the following options:");
		System.out.println("                             nw    = Don't go critical when no writer");
		System.out.println("                             ns    = Don't warn if status is 'HLD'");
		System.out.println("                             nf    = Ignore number of files in queue");
		System.out.println("                          NOTE: threshold values are used on number of files");
		System.out.println("      SBS <subsystem>   = check if the subsystem <subsystem> is running");
		System.out.println("                          NOTE: specify <subsystem> as library/subsystem");
		System.out.println("      US                = percent free storage");
		System.out.println("-h\n   Print this help screen");
		System.out.println("-V\n   Print version information");
		System.out.println("-d\n   Be verbose (debug)\n       NOTE: Needs to be one of the first arguments to work");
		System.out.println("-D\n   Be verbose and dump screen outputs (debug)");
		System.out.println("      NOTES: Needs to be one of the first arguments to work");
		System.out.println("             When things are not working, use this flag, redirect the output to");
		System.out.println("             a file and send it to me!");
		System.out.println("\nNotes:\n -CPU, DB and US threshold's are decimal, JOBS and OUTQ are integers.\n");
	}

	public static void parseCmdLineArgs(String [] args){
		int i=0;
		int READY_FLAG=127,CRIT_FLAG=64,WARN_FLAG=32,HOST_FLAG=16,USER_FLAG=8,PASS_FLAG=4,CMD_FLAG=2,ARG_FLAG=1;
		int flag=0;

		try{
			while(flag!=READY_FLAG){
				if(args[i].equals("-H")){
					ARGS.hostName=args[++i];
					flag=flag | HOST_FLAG;
				}
				else if(args[i].equals("-u")){
					ARGS.userName=args[++i];
					flag=flag | USER_FLAG;
				}
				else if(args[i].equals("-p")){
					ARGS.passWord=args[++i];
					flag=flag | PASS_FLAG;
				}
				else if(args[i].equals("-d")){
					ARGS.DEBUG=true;
				}
				else if(args[i].equals("-D")){
					ARGS.DEBUG=ARGS.DEBUG_PLUS=true;
				}
				else if(args[i].equals("-w")){
					ARGS.tHoldWarn=(new Double(args[++i])).doubleValue();
					flag=flag | WARN_FLAG;
				}
				else if(args[i].equals("-c")){
					ARGS.tHoldCritical=(new Double(args[++i])).doubleValue();
					flag=flag | CRIT_FLAG;
				}
				else if(args[i].equals("-h")){
					printUsageDetail();
					System.exit(0);
				}
				else if(args[i].equals("-V")){
					System.out.println("Version "+VERSION);
					System.exit(0);
				}
				else if(args[i].equals("-v")){
					if(args[++i].equals("CPU")){
						ARGS.command=WRKSYSSTS;
						ARGS.checkVariable=CPU;
						flag=flag | CMD_FLAG | ARG_FLAG;
					}
					else if(args[i].equals("DB")){
						ARGS.command=WRKSYSSTS;
						ARGS.checkVariable=DB;
						flag=flag | CMD_FLAG | ARG_FLAG;
					}
					else if(args[i].equals("US")){
						ARGS.command=WRKSYSSTS;
						ARGS.checkVariable=US;
						flag=flag | CMD_FLAG | ARG_FLAG;
					}
					else if(args[i].equals("AJ")){
						ARGS.command=WRKACTJOB;
						ARGS.checkVariable=AJOBS;
						flag=flag | CMD_FLAG | ARG_FLAG;
					}					
					else if(args[i].equals("JOBS")){
						ARGS.command=WRKSYSSTS;
						ARGS.checkVariable=JOBS;
						flag=flag | CMD_FLAG | ARG_FLAG;
					}
                    else if(args[i].equals("SBS")){
                            ARGS.command=DSPSBSD;
                            ARGS.checkVariable=SBS;
                            ARGS.subSystem=args[++i];
                            flag=flag | CMD_FLAG | ARG_FLAG | WARN_FLAG | CRIT_FLAG;
                    }
                    else if(args[i].equals("CJ")){
                            ARGS.command=DSPJOB;
                            ARGS.checkVariable=DJOB;
                            ARGS.job=args[++i];
                            flag=flag | CMD_FLAG | ARG_FLAG | WARN_FLAG | CRIT_FLAG;
                    }
                    else if(args[i].equals("LOGIN")){
                            ARGS.command=CMDLOGIN;
                            flag=flag | CMD_FLAG | ARG_FLAG | WARN_FLAG | CRIT_FLAG;
                    }
					else if(args[i].equals("MSG")){
						ARGS.command=DSPMSG;
						ARGS.checkVariable=MSG;
						ARGS.msgUser=args[++i];
						flag=flag | CMD_FLAG | ARG_FLAG | WARN_FLAG | CRIT_FLAG;
					}
					else if(args[i].equals("OUTQ")){
						ARGS.command=WRKOUTQ;
						ARGS.checkVariable=OUTQ;
						ARGS.outQ=args[++i];
						/*nw    = Don't warn when no writer
						ns    = Don't warn if status is 'HLD'
						nf    = Ignore number of files in queue*/
						++i;
						while(true){
							if(args[i].equals("nw")){
								ARGS.outQFlags=ARGS.outQFlags | OUTQ_NW;
								i++;
							}
							else if(args[i].equals("ns")){
								ARGS.outQFlags=ARGS.outQFlags | OUTQ_NS;
								i++;
							}
							else if(args[i].equals("nf")){
								ARGS.outQFlags=ARGS.outQFlags | OUTQ_NF;
								i++;
								flag=flag | WARN_FLAG | CRIT_FLAG;
							}
							else{
								i--;
								break;
							}
						}
						flag=flag | CMD_FLAG | ARG_FLAG;
					}
					else if(args[i].equals("CJS")){
							ARGS.command=CHKJOBSTS;
							ARGS.checkVariable=JOBSTS;
							ARGS.subSystem=args[++i];
							ARGS.job=args[++i];
												
							++i;
							while(true){
								//In this case all further addition parameters are optional
								if (i==args.length){
									flag=flag | WARN_FLAG | CRIT_FLAG; // Parameter completely set
									break;
								}
								else if(args[i].equals("status")){
									ARGS.chk_status=args[++i];
									ARGS.JobFlags=ARGS.JobFlags | JOBSTS_STATUS;
									i++;
								}
								else if(args[i].equals("noperm")){
									ARGS.JobFlags=ARGS.JobFlags | JOBSTS_NOPERM;
									i++;
								}
								else if(args[i].equals("onlyone")){
									ARGS.JobFlags=ARGS.JobFlags | JOBSTS_ONLYONE;
									i++;
								}
								else{
									i--;
									break;
								}
							}
							flag=flag | CMD_FLAG | ARG_FLAG;
					}
				}
				else{
					System.out.println("Unknown option ["+args[i]+"]");
					System.exit(WARN);
				}
				i++;
			}
			if(ARGS.checkVariable==US){
				if(ARGS.tHoldWarn<ARGS.tHoldCritical){
					System.out.println("Warning threshold should be greater than the Critical threshold.");
					System.exit(WARN);
				}
			}
			else if(ARGS.tHoldWarn>ARGS.tHoldCritical){
				System.out.println("Warning threshold should be less than the Critical threshold.");
				System.exit(WARN);
			}
		}
		catch(Exception e){
			if(ARGS.DEBUG){
				System.out.print("i : ");System.out.println(i);
				System.out.print("number of arguments : ");System.out.println(args.length-1);
				System.out.print("last argument : ");System.out.println(args[i-1]);
			}
			printUsage();
			System.exit(WARN);
		}
	}

	public static void main(String [] args){
		ARGS=new check_as400_cmd_vars();
		LANG=new check_as400_lang();
		
		parseCmdLineArgs(args);
		
		//establish connection to server
		if(ARGS.DEBUG) System.out.print("Establishing connection to server...");
		if(open()){		
			if(ARGS.DEBUG) System.out.println("done.\nLogging in...");
			//login
			if(login()){
				if(ARGS.DEBUG) System.out.println("Login completed.\nSending command ("+ARGS.command+")...");
				//send command
				String result=runCommand();
				if(result!=null){
					if(ARGS.DEBUG) System.out.println("Command sent.\nParsing results...");
					//parse and disconnect from server
					logout(parse(result));
					if(ARGS.DEBUG) System.out.println("Finished.");
				}
				else{
					System.out.println("CRITICAL - Unexpected output on command");
					logout(CRITICAL);
				}
			}
			else{
				System.out.println("CRITICAL - Unexpected output on login");
				logout(CRITICAL);
			}
		}
		else
			System.exit(CRITICAL);
		
	}
	
	public static String runCommand(){
		
		/*send proper command*/
		switch(ARGS.command){
			case WRKSYSSTS:
				send("wrksyssts astlvl(*intermed)\r");
				/*Wait and recieve output screen*/
				return waitReceive("===>",NONE);
			case WRKOUTQ:
				send("wrkoutq "+ARGS.outQ.toLowerCase()+"*\r");
				/*Wait and recieve output screen*/
				return waitReceive("===>",GETOUTQ);
			case DSPMSG:
				//send("dspmsg msgq("+ARGS.msgUser+") msgtype(*INQ) astlvl(*intermed)\r");
				send("dspmsg "+ARGS.msgUser+" astlvl(*basic)\r");
				/*Wait and recieve output screen*/
				return waitReceive("F3=",NONE);
			case DSPSBSD:
				send("dspsbsd sbsd("+ARGS.subSystem+")\r");
				/*Wait and recieve output screen*/
				return waitReceive("===>",GETSBSD);
			case DSPJOB:
				send("dspjob "+ARGS.job+"\r");
				/*Wait and recieve output screen*/
				waitReceive(LANG.SELECTION,GETJOB);
				send("1\r");
				return waitReceive("F16=",NONE);
			case WRKACTJOB:
				send("wrkactjob\r");
				/*Wait and recieve output screen*/
				return waitReceive("===>",NONE);
			case CMDLOGIN:
				System.out.println("OK - Login completed successfully");
				logout(OK);						
			case CHKJOBSTS:
				send("wrkactjob sbs("+ARGS.subSystem+") job("+ARGS.job+")\r");
				/*Wait and recieve output screen*/
				return waitReceive("===>",NONE);
			default:
				return null;
		}

	}	
	
	public static int parse(String buffer){
		switch(ARGS.command){
			case WRKSYSSTS:
				return parseWrkSysSts(buffer);
			case WRKOUTQ:
				return parseWrkOutQ(buffer);
			case DSPMSG:
				return parseDspMsg(buffer);
			case DSPSBSD:
				return parseDspSbsD(buffer);
			case DSPJOB:
				return parseDspJob(buffer);
			case WRKACTJOB:
				return parseWrkActJob(buffer);
			case CHKJOBSTS:
				return parseChkJobSts(buffer);
		}
		return UNKNOWN;
	}	
	
	public static int getStatus(double val){
		int returnStatus=UNKNOWN;
		
		if(ARGS.checkVariable==CPU || ARGS.checkVariable==DB || ARGS.checkVariable==JOBS || ARGS.checkVariable==AJOBS || ARGS.checkVariable==OUTQ){
			if(val<ARGS.tHoldWarn){
				System.out.print("OK - ");
				returnStatus=OK;
			}
			else if(val>=ARGS.tHoldWarn && val<ARGS.tHoldCritical){
				System.out.print("WARNING - ");
				returnStatus=WARN;
			}
			else{
				System.out.print("CRITICAL - ");
				returnStatus=CRITICAL;
			}
		}
		else if(ARGS.checkVariable==US){
			if(val>ARGS.tHoldWarn){
				System.out.print("OK - ");
				returnStatus=OK;
			}
			else if(val<=ARGS.tHoldWarn && val>ARGS.tHoldCritical){
				System.out.print("WARNING - ");
				returnStatus=WARN;
			}
			else{
				System.out.print("CRITICAL - ");
				returnStatus=CRITICAL;
			}
		}
		else
			System.out.print("UNKNOWN - ");
			
		return returnStatus;
		
	}
		
		/*
	public static int parseDspMsg(String buffer){
		if(buffer.indexOf(LANG.NO_MESSAGES_AVAILABLE)!=-1){
			System.out.println("OK - No messages");
			return OK;
		}
		else{
			System.out.println("WARNING - Messages needing reply");
			return WARN;
		}
	}*/
	
	public static int parseDspMsg(String paramString) {

         //when the string LANG.NO_MESSAGES_AVAILABLE is found and is before LANG.MSG_NOT_NEED_REPLY then there are no messages which need an answer
	     if(paramString.indexOf(LANG.NO_MESSAGES_AVAILABLE)!=-1){
	            if(paramString.indexOf(LANG.NO_MESSAGES_AVAILABLE)<paramString.indexOf(LANG.MSG_NOT_NEED_REPLY)){
                        System.out.println("OK - No messages | msgnum=0cnt;;;0; ");
                        return 0;
                }
         }
         
         
         //Display oldest Message.
         /*
         int i = paramString.indexOf(LANG.MSG_NEED_REPLY, 0);
         int startmessage = i+LANG.MSG_NEED_REPLY_OFFSET;
         int endresp = paramString.indexOf(")", startmessage);
         int endcut = paramString.indexOf(".", startmessage);
         int startcut=startmessage;
                  
         if (endcut>endresp) {
                startcut = endresp+2;
         }
	 */

	 int i = paramString.indexOf(LANG.MSG_NEED_REPLY, 0);
	 int startmessage = i+LANG.MSG_NEED_REPLY_OFFSET;
	 int endresp = paramString.indexOf(")", startmessage);
	 int startresp = paramString.indexOf("(", startmessage);
	 int endmessage = paramString.indexOf(".", startmessage);
	 
	 int startcut = startmessage;
	 int endcut = endmessage;
	 
	 //this takes care about the responses at the beginning (i am not sure if we need the "+5" as a treshold, but it should not hurt.)
	 if ((endmessage>endresp)&&(startresp<startmessage+5)) {
	        startcut = endresp+2;
	 }
	 
	 //this takes care about the responses just before the "."
	 if (endresp==endmessage-1) {
	        endcut = startresp;
	 }
	 
	 //when something goes terrible wrong, for examp. no responses found... just take the first 50 characters from the start of the message, they are better then nothing in case of an error
	 if ((startcut<5) || (endcut<5) || (endcut>startcut+50) || (endcut<startcut+5)) {
            startcut = startmessage;
            endcut = startmessage+50;
     }

         String str1 = paramString.substring(startcut, endcut);

         // Count MSG num 
         int k = paramString.indexOf(LANG.MSG_NOT_NEED_REPLY, 0);
         k -= 27;
         
         String str3 = paramString.substring(i, k);
         int count = 0;
	    String[] str4=new String[str3.length()];
         for(int l = 0; l < str4.length;l++) {
                 str4[l]=str3.substring(l,l+1);
                 if(str4[l].equals("(")) {
                          count++;
                 }
        }
        //MSG detial
         try {
                 String str2 = new String(str1.getBytes("ISO-8859-15"), "UTF-8");
                 System.out.println(str2+". ( "+count+" MSG need reply) | msgnum="+count+"cnt;;;0; "); 
         }
         catch (UnsupportedEncodingException localUnsupportedEncodingException) {
                 System.err.println(localUnsupportedEncodingException);
         }
         return 1;
	}
	
	
	public static int parseWrkOutQ(String buffer){
		int returnStatus=UNKNOWN;
		int start=buffer.indexOf(ARGS.outQ.toUpperCase());
		
		int files=(new Integer((buffer.substring(start+30,start+36)).trim())).intValue();
		String writer=(buffer.substring(start+42,start+51)).trim();
		String status=(buffer.substring(start+58,start+61)).trim();
		/*nw    = Don't warn when no writer     = 1
		ns    = Don't warn if status is 'HLD'   = 2 
		nf    = Ignore number of files in queue = 4 */
		if(writer.equals("[8;62H")){		
			if((ARGS.outQFlags & OUTQ_NW)!=OUTQ_NW){
				System.out.print("CRITICAL - NO WRITER - ");
				returnStatus=CRITICAL;
			}
			writer="N/A";
			status=(buffer.substring(start+54,start+58)).trim();
		}

		if(returnStatus==UNKNOWN && !(status.equals("RLS"))){
			if((ARGS.outQFlags & OUTQ_NS)!=OUTQ_NS){
				System.out.print("WARNING - QUEUE NOT RELEASED - ");
				returnStatus=WARN;
			}
		}

		if(returnStatus==UNKNOWN && (ARGS.outQFlags & OUTQ_NF)!=OUTQ_NF)
			returnStatus=getStatus(files);
			
		if(returnStatus==UNKNOWN){
			System.out.print("OK - ");
			returnStatus=OK;
		}

		System.out.println("writer("+writer+") status("+status+") files("+files+")");
		
		return returnStatus;
	}

	public static int parseDspSbsD(String buffer){
		int start=0;
		int returnStatus=UNKNOWN;

		start=findToken(buffer,":",4)+1;
		String status=(buffer.substring(start,start+9)).trim();
		if(status.equals("ACTIVE")){
			System.out.print("OK - ");
			returnStatus=OK;
		}
		else{
			if (status.equals("ACTIVA")){
			    System.out.print("OK - ");
			    returnStatus=OK;
			}
			else{
			    System.out.print("CRITICAL - ");
    			    returnStatus=CRITICAL;
    			}
		}

		System.out.println("subsystem("+ARGS.subSystem+") status("+status+")");

		return returnStatus;
	}
	
	public static int parseDspJob(String buffer){
		int start=0;
		int returnStatus=UNKNOWN;

		start=findToken(buffer,":",5)+1;
		String status=(buffer.substring(start,start+9)).trim();
		if(status.equals("ACTIVE")){
			System.out.print("OK - ");
			returnStatus=OK;
		}
		else{
			if (status.equals("ACTIVA")){
			    System.out.print("OK - ");
			    returnStatus=OK;
			}
			else{
			    System.out.print("CRITICAL - ");
    			    returnStatus=CRITICAL;
    			}		
		}

		System.out.println("job("+ARGS.job+") status("+status+")");

		return returnStatus;
	}
	
	public static int parseWrkActJob(String buffer){
		int start=0;
		int returnStatus=UNKNOWN;		

		start=findToken(buffer,":",7)+1;
		int jobs=(new Integer((buffer.substring(start,start+8)).trim())).intValue();

		returnStatus=getStatus(jobs);

		System.out.println(jobs+" active jobs in system");

		return returnStatus;
	}	
	
	public static int parseChkJobSts(String buffer){
		int start=0;
		int returnStatus=UNKNOWN;
		
		// Priority :
		// 1st : Jobstatus
		// 2nd : Existence
		
		// Check if job is in the system
		if(buffer.indexOf(LANG.NO_JOB_TO_DISPLAY)!=-1){
			if((ARGS.JobFlags&JOBSTS_NOPERM)!=JOBSTS_NOPERM)
			{
				System.out.println("CRITICAL - No Job "+ARGS.job+" in Subsystem "+ ARGS.subSystem);
				logout(CRITICAL);
			}
			else 
			{
				System.out.println("INFORMATION - No Job "+ARGS.job+" in Subsystem "+ ARGS.subSystem);	
				logout(OK);
			}
		}
		
		start=findToken(buffer,ARGS.job,2);
		String status=(buffer.substring(start+53,start+60)).trim();
		if(status.equals(ARGS.chk_status)){
			System.out.print("OK - ");
			returnStatus=OK;
			}
		else{
			if((ARGS.JobFlags & JOBSTS_STATUS) == JOBSTS_STATUS)
			{
			 System.out.print("CRITICAL - ");
    		 returnStatus=CRITICAL;
			}
			else returnStatus=OK;
    	}
		
		if (ARGS.DEBUG){
			System.out.print("Start : "); System.out.println(start);
		}
		System.out.println("job("+ARGS.subSystem+"/"+ARGS.job+") status("+status+")");

		return returnStatus;
	}
	
	public static int findToken(String buffer, String token, int instance){
		int index=0,start=-1,newStart=0;
		while(index<instance){
			start=buffer.indexOf(token,start+1);
			if(start!=-1)
				index++;
			else{
				System.out.println("Parsing ERROR!");
				logout(CRITICAL);
			}
		}
		return start;
	}

	public static String checkDouble(String buffer){
		return buffer.replace(',','.');
	}
	
	public static int parseWrkSysSts(String buffer){
		int start=0;
		int returnStatus=UNKNOWN;
		NumberFormat nf=NumberFormat.getInstance();
		nf.setMaximumFractionDigits(2);
		
		if(ARGS.checkVariable==CPU){
			start=findToken(buffer,":",3)+1;
			double cpu=(new Double(checkDouble((buffer.substring(start,start+11)).trim()))).doubleValue();

			returnStatus=getStatus(cpu);

			System.out.println("CPU Load ("+nf.format(cpu)+"%)");
		}
		else if(ARGS.checkVariable==DB){
			start=findToken(buffer,":",5)+1;
			double db=(new Double(checkDouble((buffer.substring(start,start+11)).trim()))).doubleValue();

			returnStatus=getStatus(db);

			System.out.println("DB Load ("+nf.format(db)+"%)");
		}
		else if(ARGS.checkVariable==US){
			double percentFree,total;

			start=findToken(buffer,":",10)+1;
			percentFree=100.0-(new Double(checkDouble(buffer.substring(start,start+11)))).doubleValue();
			start=findToken(buffer,":",12)+1;
			String tot=((buffer.substring(start,start+11))).trim();
			total=(new Double(checkDouble(tot.substring(0,tot.length()-1)))).doubleValue();

			returnStatus=getStatus(percentFree);

			System.out.println(nf.format(total*(percentFree/100))+" "+tot.substring(tot.length()-1)+" ("+nf.format(percentFree)+"%) free of "+((buffer.substring(start,start+11))).trim());
		}
		else if(ARGS.checkVariable==JOBS){
			start=findToken(buffer,":",11)+1;
			int jobs=(new Integer((buffer.substring(start,start+11)).trim())).intValue();

			returnStatus=getStatus(jobs);
			
			System.out.println(jobs+" jobs in system");
		}
		
		return returnStatus;
	}

	public static boolean login(){

		//if(ARGS.DEBUG) System.out.println("  sending hello...");
		/*send hello so the server will send login screen*/
		//send((char)27+"");

		if(ARGS.DEBUG) System.out.println("  waiting for screen...");
		/*Wait for the login screen*/
		if(waitReceive("IBM CORP",NONE)!=null){
			if(ARGS.DEBUG) System.out.println("  sending login information for "+ARGS.userName+"...");
			/*send login user/pass*/
			send(ARGS.userName+"\t");
			send(ARGS.passWord+"\r");
			
			if(ARGS.DEBUG) System.out.println("  waiting for login to process...");
			/*Wait and recieve command screen*/
			if(waitReceive("===>",LOGIN)!=null)
				return true;
		}
		
		return false;
	}	
	
	//Recieves all info in stream until it see's the string 'str'.
	public static String waitReceive(String str,int procedure){
		String buffer=new String();
		boolean flag=true;

		if(ARGS.DEBUG) System.out.println("    waiting for token "+str+"...");

		buffer="";
		try{
			while(flag){
				int ch;
				while((ch=ioReader.read())!=-1){
					buffer=buffer+(char)ch;

					if(ioReader.ready()==false)
						break;
				}
				if(ARGS.DEBUG_PLUS) System.out.println("\n**BUFFER IS:**\n"+buffer+"\n**END OF BUFFER**\n");
				if(procedure==LOGIN){
					if(buffer.indexOf("CPF1107")!=-1){
						System.out.println("CRITICAL - Login ERROR, Invalid password");
						close();
						System.exit(CRITICAL);
					}
					else if(buffer.indexOf("CPF1120")!=-1){
						System.out.println("CRITICAL - Login ERROR, Invalid username");
						close();
						System.exit(CRITICAL);
					}
					else if(buffer.indexOf("/"+ARGS.userName.toUpperCase()+"/")!=-1){
						if(ARGS.DEBUG) System.out.println("      responding to allocated to another job message...");
						send("\r");
						buffer="";
					}
					else if(buffer.indexOf(LANG.PASSWORD_HAS_EXPIRED)!=-1){
						send((char)27+"3");
						waitReceive(LANG.EXIT_SIGNON,NONE);
						send("Y\r");
						System.out.println("CRITICAL - Login ERROR, Expired password");
						close();
						System.exit(CRITICAL);
					}
					else if(buffer.indexOf(LANG.PASSWORD_EXPIRES)!=  -1){
						if(ARGS.DEBUG) System.out.println("      responding to password expires message...");
						send("\r");
						buffer="";
					}
					else if(buffer.indexOf(LANG.DISPLAY_MESSAGES)!=-1){
						if(ARGS.DEBUG) System.out.println("      continuing through message display...");
						send((char)27+"3");
						buffer="";
					}
				}
				else if(procedure==GETOUTQ){
					if(buffer.indexOf(LANG.NO_OUTPUT_QUEUES)!=-1){
						System.out.println("CRITICAL - outq "+ARGS.outQ+" does NOT exist");
						logout(CRITICAL);
					}
				}
                else if(procedure==GETSBSD){
                	if(buffer.indexOf(LANG.NOT_FOUND)!=-1){
                		System.out.println("CRITICAL - subsystem("+ARGS.subSystem+") NOT IN SYSTEM");
                        logout(CRITICAL);
                	}
                }
				else if(procedure==GETJOB){
					if(buffer.indexOf(LANG.DUPLICATE)!=-1){
						System.out.println("WARNING - duplicate jobs("+ARGS.job+")");
						send((char)27+"3");
						waitReceive("===>",NONE);
						logout(CRITICAL);
					}
					if(buffer.indexOf(LANG.JOB)!=-1){
						System.out.println("CRITICAL - job("+ARGS.job+") NOT IN SYSTEM");
						logout(CRITICAL);
					}					
				}
				//check for command not allowed errors
				if(procedure!=LOGIN){
					if(buffer.indexOf(LANG.LIBRARY_NOT_ALLOWED)!=-1){
						send((char)27+"3");
						System.out.println("CRITICAL - Command NOT allowed");
						logout(CRITICAL);
					}
				}
				if(buffer.indexOf(str)!=-1)
					flag=false;
				
			}
		}
		catch(Exception e) {	
			System.out.println("CRITICAL: Network error:"+e);
			return null;
		}

		if(ARGS.DEBUG) System.out.println("    token received.");

		return buffer;	
	}
	
	public static void logout(int exitStatus){
		//send F3
		if(ARGS.DEBUG) System.out.println("Logging out...\n  sending F3...");
		send((char)27+"3");
		waitReceive("===>",NONE);

		if(ARGS.DEBUG) System.out.println("  requesting signoff...");
		//send logout
		send("signoff\r");
		//waitReceive(";53H",NONE);
		waitReceive("[0m",NONE);
		if(ARGS.DEBUG) System.out.println("  terminating connection...");

		close();
		if(ARGS.DEBUG) System.out.println("Logged out.");
		System.exit(exitStatus);
	}	

	//open connection to server
	public static boolean open() {
		try {
			ioSocket=new Socket(ARGS.hostName,23);
			
			ioWriter=new PrintWriter(ioSocket.getOutputStream(),true);
			ioReader=new BufferedReader(new InputStreamReader(ioSocket.getInputStream()));

			send("\n\r");

			return true;
		}
		catch(Exception e) {
			System.out.println("CRITICAL: Network error:"+e);
			return false;
		}
	}

	//close connection to server
	public static boolean close() {
		try {
			ioSocket.close();
			return true;
		}
		catch (IOException e) {
			System.out.println("CRITICAL: Network error:"+e);
			return false;
		}
	}

	//read line from stream
	public static String read(){
		String str=new String();
		try{
			str=ioReader.readLine();
		}
		catch(Exception e) {
			System.out.println("CRITICAL: Network error: "+e);
			System.exit(CRITICAL);
		}
		return str;
	}
	
	//write str to stream
	public static void send(String str){
		try{
			ioWriter.print(str);
			ioWriter.flush();
		}
		catch(Exception e) {
			System.out.println("CRITICAL: Network error: "+e);
			System.exit(CRITICAL);
		}	
	}	

	private static Socket ioSocket;
	private static PrintWriter ioWriter;
	private static BufferedReader ioReader;
	private static check_as400_cmd_vars ARGS;
	private static check_as400_lang LANG;
	
	//These constants are for the Commands
	final static int WRKSYSSTS=0,WRKOUTQ=1,DSPMSG=2,DSPSBSD=3,DSPJOB=4,WRKACTJOB=5,CMDLOGIN=6,CHKJOBSTS=7;
	//These constants are for the variables
	final static int CPU=0,DB=1,US=2,JOBS=3,MSG=4,OUTQ=5,SBS=6,DJOB=7,AJOBS=8,JOBSTS=9;
	//These constants are for the wait recieve, controlling
	//any other logic that it should turn on. For example checking
	//for invalid login.
	final static int NONE=0,LOGIN=1,GETOUTQ=2,GETJOB=3,GETSBSD=4;
	//Theses constants are the exit status for each error type
	final static int OK=0,WARN=1,CRITICAL=2,UNKNOWN=3;
	//These constants are used as flags on OUTQ
	final static int OUTQ_NW=1,OUTQ_NS=2,OUTQ_NF=4;
	//These constants are used as flags on JOBSTS
	final static int JOBSTS_NOPERM=1,JOBSTS_ONLYONE=2,JOBSTS_STATUS=4;
};
