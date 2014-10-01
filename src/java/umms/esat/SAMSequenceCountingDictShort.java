package umms.esat;

import java.util.HashMap;
import java.util.ArrayList;

import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMSequenceRecord;

public class SAMSequenceCountingDictShort extends SAMSequenceCountingDict {
	
	// UNSTRANDED:
	//protected HashMap<String, short[]> startCounts = new HashMap<String, short[]>();      // **** short or float 
	//protected HashMap<String, ArrayList<Integer>> overflow = new HashMap<String,ArrayList<Integer>>();
	// STRANDED:
	protected HashMap<String, HashMap<String, short[]>> startCounts = new HashMap<String, HashMap<String, short[]>>();      // startCounts[chr][strand][location]
	protected HashMap<String, HashMap<String, ArrayList<Integer>>> overflow = new HashMap<String, HashMap<String, ArrayList<Integer>>>();
	
    // **** SHORT INT version limits counts to 65535
    public void incrementStartCounts(String refName, String strand, int alignStart, float fractCount) {
    	// NOTE: This effectively treats the counts as short, unsigned ints. Negative numbers indicate
		//       that the total count is >32767 and less than 65535. The test for counts!=1 limits the
		//       total counts to 65535 after converting back to an int.
    	try {
    		if (startCounts.get(refName).get(strand)[alignStart]!=-1) {
    			startCounts.get(refName).get(strand)[alignStart]++;   // increment the counter
    		} else {
    			// NOTE: since the counts arrays are short ints, counts between 32767 and 65535 will be negative.
    			//       To adjust the negative values, <correct int value> = 65536+<negative count value>)
    			//if (!overflow.containsKey(refName) || !overflow.get(refName).contains(alignStart)) {
    			if (!overflow.containsKey(refName) || !overflow.get(refName).containsKey(strand) || !overflow.get(refName).get(strand).contains(alignStart)) {
    				logger.warn("location "+alignStart+" in "+refName+" ("+strand+") has >65535 counts.");
    				// add this location to the overflow map so that only one warning is issued for overflow at this location:
    				if (!overflow.containsKey(refName)) {
    					overflow.put(refName, new HashMap<String, ArrayList<Integer>>());
    					// make overflow storage for both strands:
    					overflow.get(refName).put("+", new ArrayList<Integer>());
    					overflow.get(refName).put("-", new ArrayList<Integer>());
    				}
    				overflow.get(refName).get(strand).add(alignStart);
    			}
    		}
    	} catch (ArrayIndexOutOfBoundsException e) {
    		logger.warn("ArrayIndexOutOfBoundsError caught: "+refName+":"+alignStart);
    	}
    }

    public void copyToLocalCounts(String chr, String strand, int eStart, int cStart, int eLen, float[] floatCounts) {
    	/* copy the (unsigned short int) counts from the startCounts array to the (float) floatCounts array,
    	 * correcting for numbers >32767 and <65536 as we go
    	 */
    	short x;
    	
    	for (int i=0; i<eLen; i++){
    		x = startCounts.get(chr).get(strand)[eStart+i];
    		if (x<0) {
    			floatCounts[cStart+i] = (float) x+65536;
    		} else {
    			floatCounts[cStart+i] = (float) x;
    		}
    	}
    }
    
    public void updateCount(final SAMRecord r) {
    	/** 
    	 * increments the counter for how many reads had alignments beginning at this position.
    	 * The total counts are stored as short ints used as unsigned short ints. If the count is
    	 * negative, it indicates that the total count is >32767 and <65536, and should be converted
    	 * to the correct int value by adding 65536.
    	 * 
    	 * @param	r	a SAMRecord, a single alignment record
    	 * @see		SAMRecord
    	 */
    	String refName;
    	int alignStart;
    	float fractCount = 0; 
    	String strand;
    	
    	if (r.getReadNegativeStrandFlag()) {
    		strand = "-";
    	} else  {
    		strand = "+";
    	}
    	
    	// Check to see if storage has already been created for this reference sequence:
    	refName = r.getReferenceName();
    	alignStart = (int)(r.getAlignmentStart())-1;   // alignments are 1-based, arrays are 0-based
    	String cString = r.getCigarString(); 
    	
    	// Note: if the CigarString is "*", it indicates that the read is unmapped. It would be better 
    	//       if SAMRecord had a isMapped() method.
    	if (cString!="*" && !startCounts.containsKey(refName)) {
    		// Find the maximum coordinate of the refName in the dictionary
    		SAMSequenceRecord seq = this.getSequence(refName);
    		// Allocate a short int array for storage of the number of reads starting at each location for each strand
    		startCounts.put(refName, new HashMap<String, short[]>());
    		startCounts.get(refName).put("+", new short[seq.getSequenceLength()]);   // forward strand
    		startCounts.get(refName).put("-", new short[seq.getSequenceLength()]);   // forward strand
    	}
    	// Skip unaligned reads:
    	if (cString!="*") {
    		incrementStartCounts(refName, strand, alignStart, fractCount);
    	}
    }
    
    public boolean startCountsHasKey(String chr) {
    	return startCounts.containsKey(chr);
    }
    
    public float getStartCounts(String chr, String strand, int i) {
    		return (float) startCounts.get(chr).get(strand)[i];
    }
    
}