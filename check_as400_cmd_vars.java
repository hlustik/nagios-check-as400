//Nagios Plugin to check an IBM AS/400
//
//Developed June 2003
//Last Modified September 27 2004
//
//This class is used as a varible structure
//holding parsed command line arguments
//

public class check_as400_cmd_vars{
        //These hold the basic information we need to logon
        public String hostName,userName,passWord;

        //These store specific information on a requested operation
        public String outQ,msgUser,subSystem,job,status,chk_status,aspNums,cpuNum;

        //These keep track of what we are suppose to be checking!
        public int checkVariable,command,JobFlags,outQFlags=0;

        //these two hold the warn and critical values
        public double tHoldWarn,tHoldCritical;

        //And of course everybody needs debug variables for when
        //things get ugly!
        public boolean DEBUG=false,DEBUG_PLUS=false;
};

