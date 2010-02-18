package uk.ac.cam.ch.wwmm.opsin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Numbers fusedRings
 * @author aa593
 *
 */
class FusedRingNumberer {

	/**
	 * Sorts by atomSequences by the IUPAC rules for determining the preferred labelling
	 * The most preferred will be sorted to the back (0th position)
	 * @author dl387
	 *
	 */
	private static class SortAtomSequences implements Comparator<List<Atom>> {

	    public int compare(List<Atom> sequenceA, List<Atom> sequenceB){
	    	if (sequenceA.size() != sequenceB.size()){
	    		//Error in fused ring building. Identified ring sequences not the same lengths!
	    		return 0;
	    	}

	    	int i=0;
	    	int j=0;
	    	//Give low numbers for the heteroatoms as a set.
	    	while(i < sequenceA.size()){
				Atom atomA=sequenceA.get(i);
				boolean isAaHeteroatom =!atomA.getElement().equals("C");


				//bridgehead carbon do not increment numbering
				if (!isAaHeteroatom && atomA.getIncomingValency()>=3){
					i++;
					continue;
				}

				Atom atomB=sequenceB.get(j);
				boolean isBaHeteroatom =!atomB.getElement().equals("C");
				if (!isBaHeteroatom && atomB.getIncomingValency()>=3){
					j++;
					continue;
				}

				if (isAaHeteroatom && !isBaHeteroatom){
					return -1;
				}
				if (isBaHeteroatom && !isAaHeteroatom){
					return 1;
				}
	    		i++;j++;
	    	}

	    	i=0;
	    	j=0;
	    	//Give low numbers for heteroatoms when considered in the order: O, S, Se, Te, N, P, As, Sb, Bi, Si, Ge, Sn, Pb, B, Hg
	    	while(i < sequenceA.size()){
				Atom atomA=sequenceA.get(i);

				//bridgehead carbon do not increment numbering
				if (atomA.getElement().equals("C")&& atomA.getIncomingValency()>=3){
					i++;
					continue;
				}

				Atom atomB=sequenceB.get(j);
				if (atomB.getElement().equals("C") && atomB.getIncomingValency()>=3){
					j++;
					continue;
				}

				int atomAElementValue, atomBElementValue;
				if (heteroAtomValues.containsKey(atomA.getElement())){
					atomAElementValue = heteroAtomValues.get(atomA.getElement());
				}
				else{
					atomAElementValue=0;
				}
				if (heteroAtomValues.containsKey(atomB.getElement())){
					atomBElementValue = heteroAtomValues.get(atomB.getElement());
				}
				else{
					atomBElementValue=0;
				}
				if (atomAElementValue > atomBElementValue){
					return -1;
				}
				if (atomAElementValue < atomBElementValue){
					return 1;
				}
				i++;j++;
	    	}

	    	//Give low numbers to fusion carbon atoms.
	    	for ( i = 0; i < sequenceA.size(); i++) {
				Atom atomA=sequenceA.get(i);
				Atom atomB=sequenceB.get(i);
				if (atomA.getIncomingValency()>=3 && atomA.getElement().equals("C")){
					if (!(atomB.getIncomingValency()>=3 && atomB.getElement().equals("C"))){
						return -1;
					}
				}
				if (atomB.getIncomingValency()>=3 && atomB.getElement().equals("C")){
					if (!(atomA.getIncomingValency()>=3 && atomA.getElement().equals("C"))){
						return 1;
					}
				}
			}
	    	//Note that any sequences still unsorted at this step will have fusion carbon atoms in the same places
	    	//which means you can go through both sequences without constantly looking for fusion carbons i.e. the variable j is no longer needed

	    	//Give low numbers to fusion rather than non-fusion atoms of the same heteroelement.
	    	for (i = 0; i < sequenceA.size(); i++) {
				Atom atomA=sequenceA.get(i);
				Atom atomB=sequenceB.get(i);
				if (atomA.getIncomingValency()>=3){
					if (!(atomB.getIncomingValency()>=3)){
						return -1;
					}
				}
				if (atomB.getIncomingValency()>=3){
					if (!(atomA.getIncomingValency()>=3)){
						return 1;
					}
				}
			}
	    	return 0;
	    }
	}

	private static final HashMap<String, Integer> heteroAtomValues =new HashMap<String, Integer>();
	static{
		//unknown heteroatoms or carbon are given a value of 0
		heteroAtomValues.put("Hg",2);
		heteroAtomValues.put("B",3);
		heteroAtomValues.put("Pb",4);
		heteroAtomValues.put("Sn",5);
		heteroAtomValues.put("Ge",6);
		heteroAtomValues.put("Si",7);
		heteroAtomValues.put("Bi",8);
		heteroAtomValues.put("Sb",9);
		heteroAtomValues.put("As",10);
		heteroAtomValues.put("P",12);
		heteroAtomValues.put("N",13);
		heteroAtomValues.put("Te",14);
		heteroAtomValues.put("Se",15);
		heteroAtomValues.put("S",16);
		heteroAtomValues.put("O",17);
	}
	/**
	 * Numbers the fused ring
	 * Currently only works for a very limited selection of rings
	 * @param fusedRing
	 * @throws StructureBuildingException
	 */
	static void numberFusedRing(Fragment fusedRing) throws StructureBuildingException {
	
		List<Ring> rings = SSSRFinder.getSetOfSmallestRings(fusedRing);
	
		List<List<Atom>> atomSequences = new ArrayList<List<Atom>>();
	
		// Special case when there are only 2 rings. This is expected to be faster than a more thorough analysis
		if (rings.size() ==2){
			List<Atom> atomList =fusedRing.getAtomList();
			List<Atom> bridgeheads =new ArrayList<Atom>();
			for (Atom atom : atomList) {
				if (fusedRing.getAtomNeighbours(atom).size()==3){
					bridgeheads.add(atom);
				}
			}
			for (Atom bridgeheadAtom : bridgeheads) {
				List<Atom>  neighbours =fusedRing.getAtomNeighbours(bridgeheadAtom);
				for (Atom  neighbour :  neighbours) {
					if (!bridgeheads.contains(neighbour)){
						//found starting atom
						List<Atom> atomsVisited =new ArrayList<Atom>();
						atomsVisited.add(bridgeheadAtom);
	
						Atom nextAtom =neighbour;
						do{
							atomsVisited.add(nextAtom);
							List<Atom> possibleNextInRings =fusedRing.getAtomNeighbours( nextAtom);
							nextAtom=null;
							for (Atom nextInRing:  possibleNextInRings) {
								if (atomsVisited.contains(nextInRing)){
									//already visited
								}
								else{
									nextAtom=nextInRing;
								}
							}
						}
						while (nextAtom != null);
						atomsVisited.remove(bridgeheadAtom);
						atomsVisited.add(bridgeheadAtom);//remove the bridgehead and then re-add it so that it is at the end of the list
						atomSequences.add(atomsVisited);
					}
				}
			}
		}
		else {
			setFusedRings(rings);
	
			if (checkRingAreInChain(rings, fusedRing))
			{
				List<Ring> tRings = findTerminalRings(rings);
				Ring tRing = tRings.get(0);
	
				List<Bond> fusedBonds = tRing.getFusedBonds();
				if (fusedBonds == null || fusedBonds.size()<=0) throw new StructureBuildingException("No fused bonds found");
				if (fusedBonds.size()>1) throw new StructureBuildingException("Terminal ring connected to more than 2 rings");
	
				// if there are more, we should go through atom most counterclockwise in the ring segh all the tRings
	
				enumerateRingAtoms(rings, tRing);
				List<Ring> orderedRings = new ArrayList<Ring>();
				int[] path = getDirectionsPath(rings, fusedBonds.get(0), tRing, orderedRings);
	
				atomSequences = applyRules(path, orderedRings);
			}
			else if(checkRingsAre6Membered(rings))
			{
				int numberOfAtomsInFusedRing = fusedRing.getAtomList().size();
				atomSequences = number6MemberRings(rings, numberOfAtomsInFusedRing);
	
				if(atomSequences.size()<=0){//Error: No path found. This is either a bug in the SSSR or numbering code; assign dummy locants
					int i=1;
					for (Atom atom : fusedRing.getAtomList()) {
						atom.replaceLocant("X" + Integer.toString(i));
						i++;
					}
					return;
				}
				List<Atom> takenAtoms = atomSequences.get(0);
	
				// find missing atoms
				List<Atom> missingAtoms = new ArrayList<Atom>();
				for(Atom atom : fusedRing.getAtomList()) {
					if(!takenAtoms.contains(atom)) missingAtoms.add(atom);
				}
				// add  missing atoms to each path
				for (List<Atom> path : atomSequences) {
					for(Atom atom : fusedRing.getAtomList()) {
						if(!path.contains(atom)) path.add(atom);
					}
				}
			}
			else {
				int i=1;
				for (Atom atom : fusedRing.getAtomList()) {
					atom.replaceLocant("X" + Integer.toString(i));
					i++;
				}
				return;
			}
		}
		// find the preferred numbering scheme then relabel with this scheme
		Collections.sort( atomSequences, new SortAtomSequences());
		fusedRing.setDefaultInID(atomSequences.get(0).get(0).getID());
		FragmentTools.relabelFusedRingSystem(atomSequences.get(0));
		fusedRing.reorderAtomCollection(atomSequences.get(0));
	
	}

	//*****************************************************************************************************
	private static class ConnectivityTable {
		public final List<Ring> col1 = new ArrayList<Ring>();
		public final List<Ring> col2 = new ArrayList<Ring>();
		public final List<Integer> col3 = new ArrayList<Integer>();
		public final List<Ring> usedRings = new ArrayList<Ring>();
	}

	/**
	 * Returns possible enumerations of atoms in a 6-member ring system
	 * @param rings
	 * @param numberOfAtomsInFusedRing 
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List<List<Atom>> number6MemberRings(List<Ring> rings, int numberOfAtomsInFusedRing) throws StructureBuildingException
	{
		List<Ring> tRings = findTerminalRings(rings);
		if (tRings == null || tRings.size()<0) throw new StructureBuildingException("Terminal rings not found");
		Ring tRing = tRings.get(0);
		Bond b1 = getNonFusedBond(tRing.getBondSet());
		if(b1 == null) throw new StructureBuildingException("Non-fused bond at termial ring not found");
		// order first bring
	
		ConnectivityTable ct = new ConnectivityTable();
		buildTable(tRing, null, 0, b1, b1.getFromAtom(), ct);
	
		List<Integer> dirs = findLongestChainDirection(ct);
	
		// add all the paths together and return
		List<List<Atom>> paths = new ArrayList<List<Atom>>();
		for (Integer dir : dirs) {
			List<List<Atom>> dirPaths = findPossiblePaths(dir, ct, numberOfAtomsInFusedRing);
			for (List<Atom> path : dirPaths) {
				paths.add(path);
			}
		}
	
		return paths;
	}

	/**
	 * Finds possible variants of enumerating atoms in a given direction
	 * @param newDir
	 * @param ct
	 * @param numberOfAtomsInFusedRing 
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List<List<Atom>> findPossiblePaths(int newDir, ConnectivityTable ct, int numberOfAtomsInFusedRing) throws StructureBuildingException
	{
		//List<Integer> col3 = new  ArrayList<Integer>(this.col3);
		if ( ct.col1.size() != ct.col2.size() || ct.col2.size() != ct.col3.size() || ct.col1.size() <= 0) throw new StructureBuildingException("Sizes of arrays are not equal");
	
		int n = ct.col3.size();
		int[] col3 = new int[n];
		int maxx = 0;
		int minx = 0;
		int maxy = 0;
		int miny = 0;
	
		// turn the ring system
		int i=0;
		for(; i<n; i++)
		{
			col3[i] = changeDirectionWithHistory(ct.col3.get(i), -newDir, ct.col1.get(i).size());
		}
	
		// Find max and min coordinates for ringMap
		// we put the first ring into usedRings to start with it in the connection tbl
		int nRings = ct.usedRings.size();
		int[][] coordinates = new int[nRings][]; // correspondent to usedRings
		Ring[] takenRings = new Ring[nRings];
		int takenRingsCnt = 0;
	
		takenRings[takenRingsCnt++] = ct.col1.get(0);
		coordinates[0] = new int[]{0,0};
	
		// go through the rings in a system
		// find connected to them and assign coordinates according the directions
		// each time we go to the ring, whose coordinates were already identified.
		for(int tr=0; tr<nRings-1; tr++)
		{
			Ring c1 = takenRings[tr];
			if (c1 == null) throw new StructureBuildingException();
	
			int ic1 = ct.col1.indexOf(c1);
			int xy[] = coordinates[tr]; // find the correspondent coordinates for the ring
	
			if (ic1 >= 0)
			{
				for (int j=ic1; j<ct.col1.size(); j++)	{
					if (ct.col1.get(j) == c1)
					{
						Ring c2 = ct.col2.get(j);
						if (arrayContains(takenRings,c2)) continue;
	
						int[] newxy = new int[2];
						newxy[0] = xy[0] + Math.round(2 * countDX(col3[j]));
						newxy[1] = xy[1] + countDH(col3[j]);
	
						if(takenRingsCnt>takenRings.length) throw new StructureBuildingException("Wrong calculations");
						takenRings[takenRingsCnt] = c2;
						coordinates[takenRingsCnt] = newxy;
						takenRingsCnt++;
	
						if (newxy[0] > maxx) maxx = newxy[0];
						else if (newxy[0] < minx) { minx = newxy[0]; }
						if (newxy[1] > maxy) maxy = newxy[1];
						else if (newxy[1] < miny) { miny = newxy[1];}
					}
				}
			}
		}
		// the height and the width of the map
		int h = maxy - miny + 1;
		int w = maxx - minx + 1;
	
		Ring[][] ringMap = new Ring[w][h];
	
		// Map rings using coordinates calculated in the previous step, and transform them according to found minx and miny
	
		int ix = -minx;
		int iy = -miny;
		if (ix >= w || iy >= h) throw new StructureBuildingException("Coordinates are calculated wrongly");
		ringMap[ix][iy] = ct.col1.get(0);
	
		int curx = 0;
		int cury = 0;
		for (int ti = 0; ti<takenRings.length; ti++)
		{
			int[] xy = coordinates[ti];
			curx = xy[0] - minx;
			cury = xy[1] - miny;
			if(curx<0 || curx>w || cury<0 || cury>h) throw new StructureBuildingException("Coordinates are calculated wrongly");
			ringMap[curx][cury] = takenRings[ti];
		}
	
	
		List< int[]> chains = findChains(ringMap);
	
		// find candidates for different directions and different quadrants
	
		float[][] chainqs = new float[chains.size()][];
		// here we make array of quadrants for each chain
		for (int c=0; c<chains.size(); c++) {
			int[] chain = chains.get(c);
			int midChain = chain[0] + chain[1] - 1;
	
			float[] qs = countQuadrants(ringMap, chain[0], midChain, chain[2] );
			chainqs[c] = qs;
		}
	
		List<List<Atom>> paths = new ArrayList<List<Atom>> ();
	
		//  order for each right corner candidates for each chain
		List<String> chainCandidates = new ArrayList<String>();
		rulesBCD(chainqs, chainCandidates);
		int c = 0;
		for(String cand : chainCandidates)
		{
			List<List<Atom>> chainPaths = new ArrayList<List<Atom>> ();
			int[] chain = chains.get(c);
			int midChain = chain[0] + chain[1] - 1;
	
			for(int qi=0; qi<cand.length(); qi++) {
				int qr = Integer.parseInt(cand.charAt(qi)+"");
				Ring[][] qRingMap = transformRingWithQuadrant(ringMap, qr);
				boolean inverseAtoms = false;
				if (qr == 1 || qr == 3) inverseAtoms = true;
				List<Atom> quadrantPath = orderAtoms(qRingMap, midChain, inverseAtoms, numberOfAtomsInFusedRing);
				chainPaths.add(quadrantPath);
			}
			for (List<Atom> chainPath : chainPaths) {
				paths.add(chainPath);
			}
			c++;
		}
	
		return paths;
	}

	/**
	 * Enumerates the atoms in a system, first finds the uppermost right ring, takes the next neighbour in the clockwise direction, and so one until the starting atom is reached
	 * @param ringMap
	 * @param midChain
	 * @param inverseAtoms
	 * @param numberOfAtomsInFusedRing 
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List<Atom> orderAtoms(Ring[][] ringMap, int midChain, boolean inverseAtoms, int numberOfAtomsInFusedRing) throws StructureBuildingException
	{
		int w = ringMap.length;
		if (w<0 || ringMap[0].length < 0) throw new StructureBuildingException("Mapping results are wrong");
		int h = ringMap[0].length;
	
		List<Atom> atomPath = new ArrayList<Atom>();
	
		// find upper right ring
		Ring iRing = null;
		for (int i=w-1; i>=0; i--) {
			if (ringMap[i][h-1] != null) { iRing = ringMap[i][h-1]; break; }
		}
		if (iRing == null) throw new StructureBuildingException("Upper right ring not found");
	
		Ring prevRing = findUpperLeftNeighbour(ringMap, iRing);
		Bond prevBond = findFusionBond(iRing, prevRing);
		Bond nextBond = null;
				
		boolean finished = false;
		int stNumber;
		int endNumber;
		int size;
		int maxLoopCount = numberOfAtomsInFusedRing;
		Ring nextRing    = null;
		prevRing         = null;	// TB: otherwise the test later on would prevent going to this ring for the first time
		
		while ( ( ! finished ) && ( maxLoopCount-- > 0 ) ) // or nof rings cannot be, cause one ring can be taken 2 times; avoid endless loops
		{						
			size = iRing.size();
											
			stNumber = iRing.getBondNumber(prevBond) ;
		
			List<Bond> cbonds = iRing.getCyclicBondSet();
			List<Bond> fbonds = iRing.getFusedBonds();
			
			// changes by TB (timo.boehme@ontochem.com):
			// added loop for special case that we have two adjacent
			// fused bonds between same rings which otherwise would result in endless loop
			// i.e. with isoquinolino[6,5,4,3-cde] quinoline
			
			int bi = -1;	// bond index summand
			
			do {
			
				nextBond = null;
				bi++;
				
				if (!inverseAtoms)
				{
					for(; bi<size; bi++)
					{
						int i = (stNumber + bi + 1) % size; // +1 cause we start from the bond next to stBond and end with it
						// if this bond is fused
						Bond bond = cbonds.get(i);
						if(fbonds.contains(bond)) {
							nextBond = bond; break;
						}										
					}
				}
				else 
				{
					for(; bi<size; bi++)
					{
						int i = (stNumber - bi -1 + size) % size; // -1 cause we start from the bond next to stBond and end with it
						// if this bond is fused
						Bond bond = cbonds.get(i);
						if(fbonds.contains(bond)) {
							nextBond = bond; break;
						}										
					}
				}
				
				if (nextBond == null) throw new StructureBuildingException();
				
				// next ring
				// TB: we have to test that we don't come back to previous ring
				// via another bond
				nextRing = null;
				for (Ring ring : nextBond.getFusedRings()) {
					if(ring != iRing &&
							// test that either we come back to previous ring via same bond
							// or we go to another ring
							// maybe we should reduce this test to adjacent bonds ?
							( ( nextBond == prevBond ) || ( ring != prevRing ) ) ) {
						nextRing = ring;
						break;
					}
				}
				
			} while ( nextRing == null );
			
			endNumber = iRing.getBondNumber(nextBond) ;
			
			// Add atoms in order, considering inverse or not inverse
			if (!inverseAtoms)
			{
				Atom atom = null;
				
				// if distance between prev bond and cur bond = 1 (it means that fused bonds are next to each other), but not fused use another scheme				
				// we dont add that atom, cause it was added already
				if ( (endNumber - stNumber + size) % size != 1)
				{
					stNumber = (stNumber + 1) % size;
					endNumber = (endNumber - 1 + size ) % size;
					if (stNumber > endNumber) endNumber += size;
					
					// start from the atom next to fusion								
					for (int j = stNumber; j <= endNumber; j++) // change 4-2
					{
						atom = iRing.getCyclicAtomSet().get(j % size);
						if (atomPath.contains(atom)) { finished = true; break; }
						atomPath.add(atom);
					}
				}
			}			
			else 
			{
				Atom atom = null;
				
				// if distance between prev bond and cur bond = 1 (it means that fused bonds are next to each other), use another scheme				
				if ( ( stNumber - endNumber + size) % size != 1)
				{
					stNumber = (stNumber - 2 + size ) % size;
					endNumber = endNumber % size;				
					if (stNumber < endNumber) stNumber += size;
										
					for ( int j = stNumber; j >= endNumber; j-- ) 
					{				
						atom = iRing.getCyclicAtomSet().get(j % size);
						if (atomPath.contains(atom)) { finished = true; break;}
						atomPath.add(atom);
					}
				}
			}
			prevBond = nextBond;
			prevRing = iRing;
			iRing = nextRing;			
		}
		
		if ( ! finished )
			throw new StructureBuildingException( "Endless loop while ordering atoms of fused rings." );
			
		return atomPath;
	}

	/**
	 * Finds the neighbour ring, which is the uppermost and on the left side from the given ring. used to find previous bond for the uppermost right ring, from which we start to enumerate
	 * @param ringMap
	 * @param iRing
	 * @return
	 * @throws StructureBuildingException
	 */
	private static Ring findUpperLeftNeighbour (Ring[][] ringMap, Ring iRing) throws StructureBuildingException
	{
		Ring nRing = null;
		int minx = Integer.MAX_VALUE;
		int maxy = 0;
	
		for (Ring ring : iRing.getNeighbours())
		{
			// upper left would be previous ring
			int xy[] = findRingPosition(ringMap, ring);
			if (xy==null) throw new StructureBuildingException("Ring is not found on the map");
	
			if (xy[1] > maxy  ||  xy[1] == maxy && xy[0] < minx ) {
				maxy = xy[1];
				minx = xy[0];
				nRing = ring;
			}
		}
		return nRing;
	}

	/**
	 * Finds the position(i,j) of the ring in the map
	 * @param ringMap
	 * @param ring
	 * @return
	 * @throws StructureBuildingException
	 */
	private static int[] findRingPosition(Ring[][] ringMap, Ring ring) throws StructureBuildingException
	{
		int w = ringMap.length;
		if (w<0 || ringMap[0].length < 0) throw new StructureBuildingException("Mapping results are wrong");
		int h = ringMap[0].length;
	
		for(int i=0; i<w; i++) {
			for(int j=0; j<h; j++) {
				if (ringMap[i][j] == ring) {
					return new int[]{i,j};
				}
			}
		}
	
		return null;
	}

	/**
	 * Having upper right corner candidate transform the map to place the candidate to upper right corner
	 * @param ringMap
	 * @param rq
	 * @return
	 * @throws StructureBuildingException
	 */
	private static Ring[][] transformRingWithQuadrant(Ring[][] ringMap, int rq) throws StructureBuildingException
	{
		int w = ringMap.length;
		if (w<0 || ringMap[0].length < 0) throw new StructureBuildingException("Mapping results are wrong");
		int h = ringMap[0].length;
	
		if (rq == 0) return ringMap.clone();
	
		Ring[][] resMap = new Ring[w][h];
		for (int i=0; i<w; i++) {
			for (int j=0; j<h; j++) {
				if(rq == 1) resMap[w-i-1] [j] = ringMap[i][j];
				else if(rq == 2) resMap[w-i-1] [h-j-1] = ringMap[i][j];
				else if(rq == 3) resMap[i] [h-j-1] = ringMap[i][j];
			}
		}
	
		return resMap;
	}

	/**
	 * Finds all the chains and their data:  0-chain length, 1-iChain, 2-jChain, for current direction
	 * @param ringMap
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List< int[]> findChains(Ring[][] ringMap) throws StructureBuildingException
	{
		int w = ringMap.length;
		if (w<0 || ringMap[0].length < 0) throw new StructureBuildingException("Mapping results are wrong");
		int h = ringMap[0].length;
	
		List< int[]> chains = new ArrayList< int[]>(); // List containing int arrays with all data for each chain: 0-chain length, 1-iChain, 2-jChain
	
		int maxChain = 0;
		int chain = 0;
	
		// Find the longest chain
		for (int j=0; j<h; j++)	{
			for (int i=0; i<w; i++)	 {
				if(ringMap[i][j] != null) {
					chain = 1;
					while( i + 2*chain < w && ringMap[i + 2*chain][j] != null ) chain++; // *2 because along the x axe the step is 2
					if(chain >= maxChain) {
						int[] aChain = new int[]{chain, i, j};
						chains.add(aChain);
						maxChain = chain;
					}
					i += 2*chain;
				}
			}
		}
	
		// remove those chains that were added before we found max
		for (int i=chains.size()-1; i>=0; i--) {
			int[] aChain = chains.get(i);
			if (aChain[0] < maxChain) chains.remove(i);
		}
	
		return chains;
	}

	/**
	 * Counts number of rings in each quadrant
	 * @param ringMap
	 * @param chain
	 * @param midChain
	 * @param jChain
	 * @return
	 * @throws StructureBuildingException
	 */
	private static float[] countQuadrants(Ring[][] ringMap, int chain, int midChain, int jChain) throws StructureBuildingException
	{
		float[] qs = new float[4];
		int w = ringMap.length;
		if (w<0 || ringMap[0].length < 0) throw new StructureBuildingException("Mapping results are wrong");
		int h = ringMap[0].length;
	
		//	int midChain = iChain + chain - 1; // actually should be *2/2, because we need the middle of the chain(/2) and each step is equal to 2(*2)
	
		// Count rings in each quadrants
		for (int i=0; i<w; i++)	 {
			for (int j=0; j<h; j++)	{
				if (ringMap[i][j] == null) continue;
	
				if (i == midChain || j == jChain ) // if the ring is on the axe
				{
					if( i==midChain && j > jChain ) { qs[0]+=0.5; qs[1]+=0.5; }
					else if( i==midChain && j < jChain ) { qs[2]+=0.5; qs[3]+=0.5; }
					else if( i<midChain && j==jChain ) { qs[1]+=0.5; qs[2]+=0.5; }
					else if( i>midChain && j==jChain ) { qs[0]+=0.5; qs[3]+=0.5; }
					// if ( i==midChain && j==jChain ) we dont do anything
				}
				else if(i>midChain && j>jChain) qs[0]++;
				else if(i<midChain && j>jChain) qs[1]++;
				else if(i<midChain && j<jChain) qs[2]++;
				else if(i>midChain && j<jChain) qs[3]++;
			}
		}
	
		return qs;
	}

	/**
	 * Checks if array contains an object
	 * @param array
	 * @param c2
	 * @return
	 */
	private static boolean arrayContains(Object[] array, Object c2)
	{
		for (int i=0; i<array.length; i++) if (c2 == array[i])  return true;
		return false;
	}

	/**
	 * Finds the longest chain of rings in a line, using connectivity table
	 * @param ct
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List<Integer> findLongestChainDirection(ConnectivityTable ct) throws StructureBuildingException
	{
		if (ct.col1.size() != ct.col2.size() || ct.col2.size() != ct.col3.size()) throw new StructureBuildingException("Sizes of arrays are not equal");
	
		List<Integer> directions = new  ArrayList<Integer>();
		List<Integer> lengths = new  ArrayList<Integer>();
	
		// Ring c1;
		Ring c2;
		int curChain;
		int curDir;
		int maxChain = 0;
	
		for (int i=0; i<ct.col1.size(); i++)
		{
			c2 = ct.col2.get(i);
			curChain = 1;
			curDir = ct.col3.get(i);
			boolean chainBreak = false;
	
			while (!chainBreak)
			{
				int ic2 = ct.col1.indexOf(c2);
				boolean nextFound = false;
	
				if (ic2 >= 0)
				{
					for (int j=ic2; j<ct.col1.size(); j++)	{
						if (ct.col1.get(j) == c2 && ct.col3.get(j) == curDir)
						{
							curChain++;
							c2 = ct.col2.get(j);
							nextFound = true;
							break;
						}
					}
				}
	
				if(!nextFound)
				{
					if (curChain >= maxChain )
					{
						maxChain = curChain;
						int oDir = getOppositeDirection(curDir);
						// if we didn't have this direction before, and opposite too, it is the same orientation
						if(!directions.contains(curDir) && ! directions.contains(oDir)) {
							directions.add(curDir);
							lengths.add(curChain);
						}
					}
	
					chainBreak = true;
				}
	
			}
	
		}
	
		// take  those with length equal to max
		for (int k = lengths.size()-1; k >= 0; k--) {
			if(lengths.get(k) < maxChain){
				lengths.remove(k);
				directions.remove(k);
			}
		}
		return directions;
	}

	/**
	 * Recursive function creating the connectivity table of the rings, for each connection includs both directions
	 * @param iRing
	 * @param parent
	 * @param prevDir
	 * @param prevBond
	 * @param atom
	 * @param ct
	 * @throws StructureBuildingException
	 */
	private static void buildTable(Ring iRing, Ring parent, int prevDir, Bond prevBond, Atom atom, ConnectivityTable ct) throws StructureBuildingException
	{
	
	
		// order atoms and bonds in the ring
		iRing.makeCyclicSets(prevBond, atom);
		ct.usedRings.add(iRing);
	
		for (Ring ring : iRing.getNeighbours())
		{
			// go back to ring we come from too, take the connection 2 times
	
			// the rings that are inside are necessary, cause we consider them when counting quadrants.
			// if (ring.size() - ring.getNOFusedBonds() <=0) continue;
	
			// find direction
			Bond curBond = findFusionBond(iRing, ring);
			// calculateRingDirection(iRing, prevBond, curBond, prevDir);
	
			int dir = 0;
			if (ring == parent) {
				dir =getOppositeDirection(prevDir);
			}
			else dir = calculateRingDirection(iRing, prevBond, curBond, prevDir);
	
	
			// place into connectivity table, like graph, rings and there connection
			ct.col1.add(iRing);
			ct.col2.add(ring);
			ct.col3.add(dir);
	
			if (!ct.usedRings.contains(ring))
			{
				Atom a = getAtomFromBond(iRing, curBond);
				buildTable(ring, iRing, dir, curBond, a, ct);
			}
		}
	}

	/**
	 * Just returns any non fused bond
	 * @param bondSet
	 * @return
	 */
	private static Bond getNonFusedBond(List<Bond> bondSet)
	{
		for (Bond bond : bondSet) {
			if(bond.getFusedRings() == null || bond.getFusedRings().size() < 1)
				return bond;
		}
		return null;
	}

	/**
	 * having the direction of the bond from ring1 to ring2, returns the opposite direction: from ring2 to ring1
	 * @param prevDir
	 * @return
	 */
	private static int getOppositeDirection(int prevDir)
	{
		int dir;
		if (prevDir == 0) dir = 4;
		else if (Math.abs(prevDir) == 4) dir =0;
		else if (Math.abs(prevDir) == 1) dir = 3 * (-1) * (int) Math.signum(prevDir);
		else dir = 1 * (-1) * (int) Math.signum(prevDir);
		return dir;
	}

	/**
	 * Finds the atom connected to the bond, takes into account the order of the bonds and atoms in the ring
	 * @param ring
	 * @param curBond
	 * @return
	 * @throws StructureBuildingException
	 */
	private static Atom getAtomFromBond(Ring ring, Bond curBond) throws StructureBuildingException
	{
		if (ring.getCyclicBondSet() == null) throw new StructureBuildingException("Atoms in the ring are not ordered");
		int i=0;
		for (Bond bond : ring.getCyclicBondSet())	{
			if (bond == curBond) break;
			i++;
		}
		int ai = ( i - 1 + ring.size() ) % ring.size();
		return ring.getCyclicAtomSet().get(ai);
	}

	/**
	 * Finds the fusion bond between 2 rings
	 * @param r1
	 * @param r2
	 * @return
	 */
	private static Bond findFusionBond (Ring r1, Ring r2)
	{
		List<Bond> b2 = r2.getBondSet();
		for(Bond bond : r1.getBondSet())
			if (b2.contains(bond)) return bond;
	
		return null;
	}

	/**
	 * Calculates the direction of the next ring according to the distance between fusing bonds and the previous direction
	 * @param ring
	 * @param prevBond
	 * @param curBond
	 * @param history
	 * @return
	 * @throws StructureBuildingException
	 */
	private static int calculateRingDirection(Ring ring, Bond prevBond, Bond curBond, int history) throws StructureBuildingException
	{
		// take the ring fused to one from the previous loop step
		if ( ring.getCyclicBondSet() == null ) throw new StructureBuildingException();
		int size = ring.size();
	
		int i1 = -1;
		int i2 = -1;
		int cnt = 0;
		for(Bond bond :ring.getCyclicBondSet())
		{
			if (bond == prevBond) i1=cnt;
			if (bond == curBond) i2=cnt;
			if (i1>=0 && i2>=0) break;
			cnt++;
		}
	
		int dist = (size + i2 - i1) % size;
	
		if (dist == 0) throw new StructureBuildingException("Distance between bonds is equal to 0");
	
		return getDirectionFromDist(dist, size, history);
	}

	/**
	 * Check if all the rings in a system are 6 membered
	 * @param rings
	 * @return
	 */
	private static  boolean checkRingsAre6Membered(List<Ring> rings)
	{
		for (Ring ring : rings) {
			if (ring.size() != 6) return false;
		}
		return true;
	}

	//*****************************************************************************************************
	
	
	
	/**
	 * Finds the longest chains and call the function assigning the atoms order.
	 * @param path
	 * @param pRings
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List<List<Atom>> applyRules(int[] path, List<Ring> pRings) throws StructureBuildingException
	{
		int i;
		int si=0;
		int maxChain=0;
	
		int curDir = 0;
		List<Integer> maxDir = new ArrayList<Integer>();
	
		List<List<Atom>> allAtomOrders = new ArrayList<List<Atom>>();
	
		//int[] path = {0,0,1,1};
	
		// Find the length of the longest chain
		for (i=0; i<path.length; i++)
		{
			if (path[i] == curDir)  {
				si++;
			}
			else  // if we switch the direction
			{
				if (maxChain<si) {
					maxChain = si;
				}
	
				si = 1; // start to count current symbol
				curDir = path[i];
			}
		}
		//if sequence ends at the end of array
		if (maxChain<si) {
			maxChain = si;
		}
		// TODO change here, with delete
		curDir = 0;
		si = 0;
		for (i=0; i<path.length; i++)
		{
			if (path[i] == curDir)	{
				si++;
			}
			else  // if we switch the direction
			{
				if (maxChain == si && !maxDir.contains(curDir)) {
					maxDir.add(curDir);
				}
	
				si = 1; // start to count current symbol
				curDir = path[i];
			}
		}
		if (maxChain == si && !maxDir.contains(curDir)) {
			maxDir.add(curDir);
		}
	
		if (maxDir.size()<=0) throw new StructureBuildingException("Chains are not recognized in the molecule");
	
		for (int dir : maxDir) {
			List<List<Atom>> orders = getOrderInEachDirection(path, pRings, dir, maxChain);
			for (List<Atom> order : orders) {
				allAtomOrders.add (order);
			}
		}
	
		return allAtomOrders;
	}

	/**
	 * get the direction of the main chain, according to it recalculates the path, check the rule, calculating number of rings in each quadrant
	 * @param path
	 * @param pRings
	 * @param maxDir
	 * @param maxChain
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List<List<Atom>> getOrderInEachDirection(int[] path, List<Ring> pRings, int maxDir, int maxChain) throws StructureBuildingException
	{
		//for further analyses we change the orientation.
		int i;
		List<String> chainVariants = new ArrayList<String>();
		boolean sequence = false;
		path = path.clone(); // not to change real object
	
		//  if 4 than change the path and the order of rings
		if (Math.abs(maxDir) == 4)
		{
			int[] copyPath = path.clone();
			List<Ring> inversedRings = new ArrayList<Ring>();
			int l = path.length;
	
			if (pRings.size() < path.length) throw new StructureBuildingException("The path does not correspond to array of rings");
			for (i=0; i<l; i++) {
				if ( copyPath[i] == 0 ) path[l-i-1] = 4;
				else if ( Math.abs( copyPath[i] ) == 1 ) path[l-i-1] = 3 * (int) Math.signum(copyPath[i]) * (-1);
				else if ( Math.abs( copyPath[i] ) == 3 ) path[l-i-1] = 1 * (int) Math.signum(copyPath[i]) * (-1);
				else if ( Math.abs( copyPath[i] ) == 4 ) path[l-i-1] = 0;
	
				inversedRings.add(pRings.get(l-i));
			}
			inversedRings.add(pRings.get(0));
			pRings = inversedRings; // change the reference, but not changing initial values of pRings
		}
		// changed with function
		else if (maxDir != 0 ){
			for (i=0; i<path.length; i++) {
				path[i] = changeDirectionWithHistory(path[i], -maxDir, pRings.get(i).size());
			}
		}
	
		// Rule A
		List<Integer> chains = new ArrayList<Integer>();
		// find the longest chains
		int si=0;
		for (i=0; i<path.length; i++)
		{
			if (path[i] == 0 ) //|| path[i] == 4)
			{
				if (!sequence) sequence  = true;
				si++;
			}
			else if (sequence) // if we finished the chain
			{
				sequence = false;
				if (maxChain==si) chains.add(i-maxChain); // the begining of the chain
				si=0;
			}
		}
		// if chain  finishes with the end of path
		if (sequence && maxChain==si) chains.add(i-maxChain);
	
	
		float[][] qs = new float [chains.size()][];
		int c=0;
	
		for (Integer chain : chains)
		{
			qs[c] = countQuadrants(chain, maxChain, path);
			c++;
		}
	
		rulesBCD(qs, chainVariants);
	
		int ichain = 0;
	
		List<Ring> inversedRings = new ArrayList<Ring>();
		for (int k=pRings.size()-1; k>=0; k--) { // make once inversed, dont repeat
			inversedRings.add(pRings.get(k));
		}
	
		List<List<Atom>> atomOrders = new ArrayList<List<Atom>>();
	
		for (String chain :chainVariants)
		{
			for (int j=0; j<chain.length(); j++)
			{
				int q = Integer.parseInt(chain.charAt(j)+ "");
	
				boolean inverseAtoms = false;
				if (q == 1 || q ==3) inverseAtoms = true;
	
				int stChain = chains.get(ichain);
	
				List<Ring> ringsToPass;
				if (q == 1 || q == 2) {
					stChain = path.length - stChain - maxChain;
					ringsToPass = inversedRings;
				}
				else ringsToPass = new ArrayList<Ring>(pRings);
	
				List<Atom> oAtoms = createAtomOrder(ringsToPass, getTransformedPath(path, q), stChain, maxChain, q, inverseAtoms);
				atomOrders.add(oAtoms);
	
			}
			ichain++;
		}
	
		return  atomOrders;
	}

	/**
	 * Applying rules B, C and D for the ring system. The function is used for both types of ring systems.
	 * @param qs - array with number of ring in each quadrant for each chain.
	 * @param chainVariants
	 * @throws StructureBuildingException
	 */
	private static void rulesBCD(float[][] qs, List<String> chainVariants) throws StructureBuildingException
	{
		// Analyse quadrants
	
		// Rule B: Maximum number of rings in upper right quadrant. Upper right corner candidates
		int variantNumber = 0;
		float qmax = 0;
		int c=0;
		int nchains = qs.length;
	
		for (c=0; c<nchains; c++)
		{
			for (int j=0; j<4; j++)	{
				if(qs[c][j]>qmax) qmax = qs[c][j];
			}
		}
	
		for (c=0; c<nchains; c++)
		{
			String taken = "";
			for (int j=0; j<4; j++){
				if (qs[c][j]==qmax) { taken += j; variantNumber++; }
			}
			chainVariants.add(taken);
		}
	
	
	
		// Rule C: Minimum number of rings in lower left quadrant
		if (variantNumber > 1)
		{
			c=0;
			variantNumber = 0;
			float qmin = Integer.MAX_VALUE;
	
			for (String chain : chainVariants) {
				for (int j=0; j<chain.length(); j++)
				{
					int q = Integer.parseInt(chain.charAt(j)+ "");
					int qdiagonal = (q + 2) % 4;
					if (qs[c][qdiagonal]<qmin) qmin = qs[c][qdiagonal];
				}
				c++;
			}
			c=0;
			for (String chain : chainVariants) {
				String taken = "";
				for (int j=0; j<chain.length(); j++)
				{
					int q = Integer.parseInt(chain.charAt(j)+ "");
					int qdiagonal = (q + 2) % 4;
					if (qs[c][qdiagonal]==qmin) { taken += q; variantNumber++;}
				}
				chainVariants.set(c, taken);
				c++;
			}
		}
		else if (variantNumber <= 0)
			throw new StructureBuildingException("Atom enumeration path not found");
	
	
		// Rule D: Maximum number of rings above the horizontal row
		if (variantNumber > 1)
		{
			c=0;
			float rmax = 0;
			variantNumber = 0;
			for (String chain : chainVariants) {
				for (int j=0; j<chain.length(); j++)
				{
					int q = Integer.parseInt(chain.charAt(j)+ "");
					int qrow;
					if (q % 2 == 0) qrow = q + 1;
					else qrow = q - 1;
	
					if (qs[c][qrow] + qs[c][q] > rmax) rmax = qs[c][qrow] + qs[c][q];
				}
				c++;
			}
			c=0;
			for (String chain : chainVariants) {
				String taken = "";
				for (int j=0; j<chain.length(); j++)
				{
					int q = Integer.parseInt(chain.charAt(j)+ "");
					int qrow;
					if (q % 2 == 0) qrow = q + 1;
					else qrow = q - 1;
	
					if (qs[c][qrow] + qs[c][q] == rmax) { taken += q; variantNumber++; }
				}
				chainVariants.set(c, taken);
				c++;
			}
		}
	
		if (variantNumber <= 0)
			throw new StructureBuildingException("Atom enumeration path not found");
	
	
	
	}

	/**
	 * Adds atoms to the array in the order according to the rules,. Finds the uppermost right ring, starting from it adds atoms to the result List
	 * @param rings
	 * @param path
	 * @param chainStart
	 * @param chainLen
	 * @param quadrant
	 * @param inverseAtoms
	 * @return
	 * @throws StructureBuildingException
	 */
	private static List<Atom> createAtomOrder(List<Ring> rings, int[] path, int chainStart, int chainLen, int quadrant, boolean inverseAtoms) throws StructureBuildingException
	{
		// atom order is changed when we transform the right corner from the 1st and 3d quadrant (start from 0)
		// rings order is inversed when we transform from 1st and 2d quadrant (start from 0)
		List<Atom> atomPath = new ArrayList<Atom>();
	
		// find the upper right ring
		int height=0;
		int maxheight = 0;
		float xdist = (float) chainLen / 2;
		float maxDist = xdist;
		boolean foundAfterChain = true;
		int[] ringHeights = new int[path.length+1];
	
		int  i = chainStart + chainLen;
		int upperRightPath = i-1;// if nothing after chain
	
		for ( ; i<path.length; i++)
		{
			height += countDH(path[i]);
			xdist += countDX(path[i]);
			ringHeights[i+1] = height;
	
			// take the ring if it is the highest and in the right quadrant, and then take the most right
			if ( (height > maxheight && xdist >= 0) || (height == maxheight &&  xdist > maxDist) )
			{
				maxheight = height;
				maxDist = xdist;
				upperRightPath = i;
			}
		}
	
		// if we assume that the path can come from the left side to the right, then we should check the beginning of the path
		height=0;
		xdist = -(float) chainLen / 2;
	
		i = chainStart - 1;
	
		for ( ; i>=0; i--)
		{
			height -= countDH(path[i]);
			xdist -= countDX(path[i]);
			ringHeights[i+1] = height;
	
			// take the ring if it is the highest and in the right quadrant, and then take the most right
			if ( (height > maxheight && xdist >= 0) || (height == maxheight &&  xdist > maxDist) )
			{
				maxheight = height;
				maxDist = xdist;
				upperRightPath = i;
				foundAfterChain = false;
			}
		}
		//  if we found the ring by backtracing we dont need to decrease
		if (foundAfterChain) upperRightPath++; // because we have 1 less elements in the path array
		if (upperRightPath<0 || upperRightPath>rings.size()) throw new StructureBuildingException();
	
	
		List<Bond> fusedBonds;
		Bond prevFusedBond = null;
		Bond curFusedBond = null;
		Ring ring = rings.get(upperRightPath);
		boolean finished = false;
	
		// if only one fused bond - the terminal ring is the first ring
		// otherwise we need to find the "previous" bond
		fusedBonds = ring.getFusedBonds();
		if (fusedBonds.size()==1) prevFusedBond = null;
		else
		{
			// the ring should be between 2 rings, otherwise it is terminal
			if (upperRightPath+1 >= ringHeights.length || upperRightPath-1 < 0) throw new StructureBuildingException();
	
			Ring prevRing = null;
	
			if (ringHeights[upperRightPath-1] > ringHeights[upperRightPath+1]) prevRing = rings.get(upperRightPath-1);
			else prevRing = rings.get(upperRightPath+1);
	
			for (Bond bond : fusedBonds) {
				if (bond.getFusedRings().contains(prevRing)) { prevFusedBond = bond; break;}
			}
			if (prevFusedBond == null) throw new StructureBuildingException();
		}
	
	
		while (!finished) // we go back from the right corner   // we can also ask if there this ring is equal to the first one
		{
			fusedBonds = ring.getFusedBonds();
	
			// if there is only one fused bond: EITHER the current would be that one and prev=null (1st iteration) OR current would be equal to prev.
			for (Bond bond : fusedBonds) {
				if (bond != prevFusedBond) {
					curFusedBond = bond;
				}
			}
	
			if (prevFusedBond==null) prevFusedBond = curFusedBond;
	
			int size = ring.size();
	
			int stNumber = ring.getBondNumber(prevFusedBond) ;
			int endNumber = ring.getBondNumber(curFusedBond) ;
	
			if (!inverseAtoms)
			{
				Atom atom = null;
	
				// if distance between prev bond and cur bond = 1 (it means that fused bonds are next to each other), use another scheme
				// we dont add that atom, cause it was added already
				if ( (endNumber - stNumber + size) % size != 1)
				{
					stNumber = (stNumber + 1) % size;
					endNumber = (endNumber - 1 + size ) % size;
					if (stNumber > endNumber) endNumber += size;
	
					// start from the atom next to fusion
					for (int j = stNumber; j <= endNumber; j++) // change 4-2
					{
						atom = ring.getCyclicAtomSet().get(j % size);
						if (atomPath.contains(atom)) { finished = true;  break; }
						atomPath.add(atom);
					}
				}
			}
			else
			{
				Atom atom = null;
	
				// if distance between prev bond and cur bond = 1 (it means that fused bonds are next to each other), use another scheme
				if ( ( stNumber - endNumber + size) % size != 1)
				{
					stNumber = (stNumber - 2 + size ) % size;
					endNumber = endNumber % size;
					if (stNumber < endNumber) stNumber += size;
	
					for ( int j = stNumber; j >= endNumber; j-- )
					{
						atom = ring.getCyclicAtomSet().get(j % size);
						if (atomPath.contains(atom)) { finished = true; break; }
						atomPath.add(atom);
	
					}
				}
			}
	
			if (finished) break;
	
			List<Ring> fusedRings = curFusedBond.getFusedRings();
			for (Ring fRing : fusedRings) {
				if (ring != fRing) { ring = fRing; break;}
			}
	
			prevFusedBond = curFusedBond;
		}
	
		return atomPath;
	}

	/**
	 * Transforms the given path according to the quadrant proposed as the right corner
	 * @param path
	 * @param urCorner
	 * @return
	 */
	private static int[] getTransformedPath(int[] path, int urCorner)
	{
		int l = path.length;
	
		int[] rPath = new int[l];
	
		for(int i=0; i<l; i++)
		{
			if(urCorner == 1){
				rPath[i] = -path[l-i-1]; // changes ring order
			}
			else if(urCorner == 2){
				rPath[i] = path[l-i-1]; // changes ring order
			}
			else if(urCorner == 3){
				rPath[i] = -path[i];
			}
			else{
				rPath[i] = path[i];
			}
		}
		return rPath;
	}

	/**
	 * Counts number of rings in each quadrant
	 * @param start
	 * @param len
	 * @param path
	 * @return
	 */
	private static float[] countQuadrants(int start, int len, int[] path)
	{
		int i;
		float[] quadrants = new float[4]; // 0-ur, 1-ul, 2-ll, 3-lr, counter-clockwise
		int height = 0;
		float xdist = -(float) len/ 2;
	
		// count left side
		for (i=start-1; i>=0; i--)
		{
			height -= countDH(path[i]);
			xdist -= countDX(path[i]);
			incrementQuadrant(height, xdist, quadrants);
		}
	
		height = 0;
		xdist = (float) len/ 2;
		// count right side
		for (i=start+len; i<path.length; i++)
		{
			height += countDH(path[i]);
			xdist += countDX(path[i]);
	
			incrementQuadrant(height, xdist, quadrants);
		}
	
		return quadrants;
	}

	/**
	 * Used to add the number of the rings to a quadrant, according to the position of the ring
	 * @param height
	 * @param xdist
	 * @param quadrants
	 */
	private static void incrementQuadrant(int height, float xdist, float[] quadrants)
	{
		if (height > 0)
		{
			if (xdist>0) quadrants[0]+=1; // right side
			else if (xdist<0) quadrants[1]+=1; // left side
			else {  // middle
				 quadrants[0]+=0.5;
				 quadrants[1]+=0.5;
			}
		}
		else if (height < 0)
		{
			if (xdist>0) quadrants[3]+=1; // right side
			else if (xdist<0) quadrants[2]+=1; // left side
			else {  // middle
				 quadrants[3]+=0.5;
				 quadrants[2]+=0.5;
			}
		}
		else // height=0
		{
			if (xdist>0)
			{
				quadrants[0]+=0.5f;
				quadrants[3]+=0.5f;
			}
			else if (xdist<0)
			{
				quadrants[1]+=0.5f;
				quadrants[2]+=0.5f;
			}
			else {
				 // should actually add 0.25 to each, but this doesnt make any change.
			}
		}
	}

	/**
	 * Counts delta x distance between previous and next rings
	 * @param val
	 * @return
	 */
	private static float countDX (int val)
	{
		float dx = 0;
	
		if (Math.abs(val) == 1) dx += 0.5f;
		else if (Math.abs(val) == 3) dx -= 0.5f;
		else if (Math.abs(val) == 0) dx += 1f;
		else if (Math.abs(val) == 4) dx -= 1f;
	
		return dx;
	}

	/**
	 * Counts delta height between previous and next rings
	 * @param val
	 * @return
	 */
	
	private static int countDH(int val)
	{
		int dh = 0;
		if (Math.abs(val) != 4)
		{
			if (val>0) dh = 1;
			if (val<0) dh = -1;
		}
		return dh;
	}

	/**
	 * Finds the rings with the min fused bonds
	 * @param rings
	 * @return
	 */
	private static List<Ring> findTerminalRings(List<Ring> rings)
	{
		List<Ring> tRings = new ArrayList<Ring>();
	
		int minFusedBonds = Integer.MAX_VALUE;
		for  (Ring ring : rings)
		{
			if (ring.getNumberOfFusedBonds() < minFusedBonds) minFusedBonds = ring.getNumberOfFusedBonds();
		}
	
		for  (Ring ring : rings)
		{
			if (ring.getNumberOfFusedBonds() == minFusedBonds) tRings.add(ring);
		}
		return tRings;
	}

	/**
	 * Fills the value fusedRings for bonds, calcuclates the number of fused bonds in a ring
	 * @param rings
	 */
	private static void setFusedRings(List<Ring> rings)
	{
		for (Ring curRing : rings) {
			for(Bond bond : curRing.getBondSet()) { 			// go through all the bonds for the current ring
				if (bond.getFusedRings().size()>=2) continue; 	// it means this bond we already analysed and skip it
	
				for (Ring ring : rings) {  						// check if this bond belongs to any other ring
					if (curRing != ring) {
						if (ring.getBondSet().contains(bond)) {
							bond.addFusedRing(ring);			// if so, then add the rings into fusedRing array in the bond
							bond.addFusedRing(curRing);			// and decrease number of free bonds for both rings
	
							ring.incrementNumberOfFusedBonds();
							curRing.incrementNumberOfFusedBonds();
	
							ring.addNeighbour(curRing);
							curRing.addNeighbour(ring);
						}
					}
				}
			}
		}
	}

	/**
	 * Checks if the given fragment is chain type ring system
	 * @param rings
	 * @param frag
	 * @return
	 */
	private static boolean checkRingAreInChain(List<Ring> rings, Fragment frag)
	{
		for (Ring ring : rings) {
			if (ring.getNumberOfFusedBonds() > 2) return false;
			if (ring.size()>9) return false;
		}
	
		for (Atom atom : frag.getAtomList()){
			Set<Bond> bonds = atom.getBonds();
			if (bonds.size()>2){
				int nFused = 0;
				for (Bond bond : bonds) {
					if (bond.getFusedRings().size()>1) nFused++;
				}
				if (nFused>2) return false;
			}
		}
		return true;
	}

	/**
	 * Orders atoms in each ring, enumeration depends on enumeration of previous ring
	 * @param rings
	 * @param tRing
	 * @throws StructureBuildingException
	 */
	private static void enumerateRingAtoms(List<Ring> rings, Ring tRing) throws StructureBuildingException
	{
		if (rings == null || rings.size()<=0) throw new StructureBuildingException();
	
		Ring iRing = tRing;
		Bond stBond = tRing.getBondSet().get(0);
		Atom stAtom = stBond.getToAtom();
	
		for (int i=0; i<rings.size(); i++)
		{
			iRing.makeCyclicSets(stBond, stAtom);
	
			if (i==rings.size()-1) break;
	
			// find the bond between current ring and next one
			List<Bond> fusedBonds = iRing.getFusedBonds();
			if (fusedBonds == null || fusedBonds.size()<=0) throw new StructureBuildingException();
			for (Bond fBond : fusedBonds) {
				if (stBond != fBond) {stBond = fBond; break;} // we take the bond different from the current
			}
	
			int cnt = 0;
			for (Bond bond : iRing.getCyclicBondSet()) {
				if (bond == stBond)	{
					cnt--;
					if (cnt<0) cnt = iRing.size()-1;
					stAtom = iRing.getCyclicAtomSet().get(cnt); // so that the enumeration go the same direction we give the previous atom
					break;
				}
				cnt++;
			}
	
			// take next ring fused to the current
			List<Ring> fusedRings = stBond.getFusedRings();
			if (fusedRings == null || fusedRings.size()<2) throw new StructureBuildingException();
			if (iRing != fusedRings.get(0)) iRing = fusedRings.get(0);
			else iRing = fusedRings.get(1);
		}
	
	}

	/**
	 * Creates an array describing mutual position of the rings
	 * @param rings
	 * @param startBond
	 * @param tRing
	 * @return
	 * @throws StructureBuildingException
	 */
	private static int[] getDirectionsPath(List<Ring> rings, Bond startBond, Ring tRing, List<Ring> orderedRings) throws StructureBuildingException
	{
		//String fPath = tRing.size() + "R"; // because from the first ring we go right
		int[] path = new int[rings.size()-1];
		path[0]=0;
		orderedRings.add(tRing);
	
		Ring iRing = tRing;
		Bond stBond = startBond;
		Bond nextBond=null;
		int history=0; // we store here the previous  direction
	
		for (int i=0; i<rings.size()-1; i++)
		{
			// take the ring fused to one from the previous loop step
			List<Ring> fusedRings = stBond.getFusedRings();
			if (fusedRings == null || fusedRings.size()<2) throw new StructureBuildingException();
			if (iRing != fusedRings.get(0)) iRing = fusedRings.get(0);
			else iRing = fusedRings.get(1);
	
			int size = iRing.size();
			orderedRings.add(iRing);
	
			// find the next fused bond between current ring and the next
			List<Bond> fusedBonds = iRing.getFusedBonds();
			if (fusedBonds == null || fusedBonds.size()<=0) throw new StructureBuildingException();
			if (fusedBonds.size() == 1) break;		// we came to the last ring in the chain
			for (Bond fBond : fusedBonds) {
				if (stBond != fBond) {nextBond = fBond; break; } // we take the bond different from the current
			}
	
			int i1 = -1;
			int i2 = -1;
			int cnt = 0;
			for(Bond bond :iRing.getCyclicBondSet())
			{
				if (bond == stBond) i1=cnt;
				if (bond == nextBond) i2=cnt;
				if (i1>=0 && i2>=0) break;
				cnt++;
			}
	
			int dist = (size + i2 - i1) % size;
	
			if (dist == 0) throw new StructureBuildingException("Distance between fused bonds is equal to 0");
	
			int dir = getDirectionFromDist(dist, size, history);
	
			history = dir;
	
			path[i+1] = dir;
	
			stBond = nextBond;
		}
	
		return path;
	}

	// take history! or make just 2 directions
	private static int getDirectionFromDist(int dist, int size, int history) throws StructureBuildingException
	{
		// positive val of n - Up
		// negative value - Down
	
		int dir=0;
	
		if (size >= 10) throw new StructureBuildingException("rings with more than 10 members are not recognized");
	
		if (size == 3) // 3 member ring
		{
			if (dist == 1) dir = 1;
			else if (dist == 2) dir = -1;
			else throw new StructureBuildingException();
		}
		else if (size == 4) // 4 member ring
		{
			if (dist == 2) dir = 0;
			else if (dist < 2) dir = 2;
			else if (dist > 2) dir = -2;
		}
	
		else if (size % 2 == 0) // even
		{
			if (dist == 1) dir = 3;
			else if (dist == size-1) dir = -3;
	
			else
			{
				dir = size/2 - dist;
				// 8 and more neighbours
				if (Math.abs(dir) > 2 && size >= 8) dir = 2 * (int) Math.signum(dir);
			}
		}
		else // odd
		{
			if (dist == size/2 || dist == size/2 + 1)  dir = 0;
			else if (size == 5) dir =2;
	
			else if (dist == size-1) dir = -3;
			else if (dist == 1) dir = 3;
	
			else if (size>=9 && dist == size/2-1) dir = 2; // direction number 2 appears only when
			else if (size>=9 && dist == size/2+2) dir = 2;
	
			else if(dist < size/2) dir = 2;
			else if(dist > size/2+1) dir = -2;
		}
	
		dir =changeDirectionWithHistory(dir, history, size);
		return dir;
	
	}

	private static int changeDirectionWithHistory(int dir, int history, int size)
	{
		int relDir = dir;
	
		if (Math.abs(history) == 4)
		{
			if (dir == 0) dir = 4;
			else
				dir += 4 * (-1) * Math.signum(dir); // if dir<0 we add 4, if dir>0 we add -4
		}
		else
			dir += history;
	
		if (Math.abs(dir)>4) // Added
		{
			dir = Math.round( (8 - Math.abs(dir)) * Math.signum(dir) * (-1) );
		}
	
		// 6 member ring does not have direction 2
		if (size == 6 && Math.abs(dir) == 2)
		{
			//if (history == 1 || history == -3) dir++;
			//else if (history == 3 || history == -1) dir--;
			// changed
			// if (one of them equal to 1 and another is equal to 3, we decrease absolute value and conserve the sign)
			if (Math.abs(relDir)==1 && Math.abs(history)==3  ||  Math.abs(relDir)==3 && Math.abs(history)==1) {dir = 1 * (int) Math.signum(dir);}
			// if both are equal to 1
			else if(Math.abs(relDir)==1 && Math.abs(history)==1 ) {dir = 3 * (int) Math.signum(dir);}
			// if both are equal to 3
			else if(Math.abs(relDir)==3 && Math.abs(history)==3 ) {dir = 3 * (int) Math.signum(dir);}
			// else it is correctly 2 // else throw new StructureBuildingException();
		}
	
		if (dir == -4) dir = 4;
	
		return dir;
	}
}