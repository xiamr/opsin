package uk.ac.cam.ch.wwmm.opsin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.ac.cam.ch.wwmm.opsin.WordRules.WordRule;
import static uk.ac.cam.ch.wwmm.opsin.XmlDeclarations.*;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;

/**Does destructive procedural parsing on parser results.
 *
 * @author ptc24/dl387
 *
 */
class PostProcessor {

	/**
	 * Sort bridges such as the highest priority secondary bridges come first
	 * e.g. 1^(6,3).1^(15,13)
	 * rearranged to 1^(15,13).1^(6,3)
	 * @author dl387
	 *
	 */
	private class VonBaeyerSecondaryBridgeSort implements Comparator<HashMap<String, Integer>> {

	    public int compare(HashMap<String, Integer> bridge1, HashMap<String, Integer> bridge2){
	    	//first we compare the larger coordinate, due to an earlier potential swapping of coordinates this is always in  "AtomId_Larger"
	    	int largerCoordinate1 = bridge1.get("AtomId_Larger");
	    	int largerCoordinate2 = bridge2.get("AtomId_Larger");
			if (largerCoordinate1 >largerCoordinate2) {
				return -1;
			}
			else if (largerCoordinate2 >largerCoordinate1) {
				return 1;
			}
			//tie
	    	int smallerCoordinate1 = bridge1.get("AtomId_Smaller");
	    	int smallerCoordinate2 = bridge2.get("AtomId_Smaller");
			if (smallerCoordinate1 >smallerCoordinate2) {
				return -1;
			}
			else if (smallerCoordinate2 >smallerCoordinate1) {
				return 1;
			}
			//tie
	    	int bridgelength1 = bridge1.get("Bridge Length");
	    	int bridgelength2 = bridge2.get("Bridge Length");
			if (bridgelength1 >bridgelength2) {
				return -1;
			}
			else if (bridgelength2 >bridgelength1) {
				return 1;
			}
			else{
				return 0;
			}
	    }
	}

	//match a fusion bracket with only numerical locants. If this is followed by a HW group it probably wasn't a fusion bracket
	private final Pattern matchNumberLocantsOnlyFusionBracket = Pattern.compile("\\[\\d+[a-z]?(,\\d+[a-z]?)*\\]");
	private final Pattern matchVonBaeyer = Pattern.compile("(\\d+[^\\.,]*\\d*,?\\d*[^\\.,]*)");//the not a period or comma allows some an optional indication that the following comma separated digits are superscripted
	private final Pattern matchAnnulene = Pattern.compile("[\\[\\(\\{]([1-9]\\d*)[\\]\\)\\}]annulen");
	private final String elementSymbols ="(?:He|Li|Be|B|C|N|O|F|Ne|Na|Mg|Al|Si|P|S|Cl|Ar|K|Ca|Sc|Ti|V|Cr|Mn|Fe|Co|Ni|Cu|Zn|Ga|Ge|As|Se|Br|Kr|Rb|Sr|Y|Zr|Nb|Mo|Tc|Ru|Rh|Pd|Ag|Cd|In|Sn|Sb|Te|I|Xe|Cs|Ba|La|Ce|Pr|Nd|Pm|Sm|Eu|Gd|Tb|Dy|Ho|Er|Tm|Yb|Lu|Hf|Ta|W|Re|Os|Ir|Pt|Au|Hg|Tl|Pb|Bi|Po|At|Rn|Fr|Ra|Ac|Th|Pa|U|Np|Pu|Am|Cm|Bk|Cf|Es|Fm|Md|No|Lr|Rf|Db|Sg|Bh|Hs|Mt|Ds)";
	private final Pattern matchStereochemistry = Pattern.compile("(.*?)(SR|RS|[RSEZrsez])");
	private final Pattern matchStar = Pattern.compile("\\*");
	private final Pattern matchRS = Pattern.compile("[RSrs]");
	private final Pattern matchEZ = Pattern.compile("[EZez]");
	private final Pattern matchLambdaConvention = Pattern.compile("(\\S+)?lambda\\D*(\\d+)\\D*");
	private final Pattern matchComma =Pattern.compile(",");
	private final Pattern matchSemiColon =Pattern.compile(";");
	private final Pattern matchDot =Pattern.compile("\\.");
	private final Pattern matchNonDigit =Pattern.compile("\\D+");
	private final Pattern matchSuperscriptedLocant = Pattern.compile("(" + elementSymbols +"'*).*(\\d+[a-z]?'*).*");
	private final Pattern matchIUPAC2004ElementLocant = Pattern.compile("(\\d+'*)-(" + elementSymbols +"'*)");
	private final Pattern matchInlineSuffixesThatAreAlsoGroups = Pattern.compile("carbonyl|oxy|sulfenyl|sulfinyl|sulfonyl|selenenyl|seleninyl|selenonyl|tellurenyl|tellurinyl|telluronyl");

	private final ResourceManager resourceManager;

	PostProcessor(ResourceManager resourceManager) {
		this.resourceManager = resourceManager;
	}

	/** The master method, postprocesses a parse result.
	 *
	 * @param moleculeEl The element to postprocess.
	 * @param state
	 * @return
	 * @throws Exception
	 */
	void postProcess(Element moleculeEl, BuildState state) throws Exception {
		/* Throws exceptions for occurrences that are ambiguous and this parse has picked the incorrect interpretation */
		resolveAmbiguities(moleculeEl);

		List<Element> substituentsAndRoot = XOMTools.getDescendantElementsWithTagNames(moleculeEl, new String[]{SUBSTITUENT_EL, ROOT_EL});

		for (Element subOrRoot: substituentsAndRoot) {
			processLocants(subOrRoot);
			processAlkaneStemModifications(subOrRoot);//e.g. tert-butyl
			processHeterogenousHydrides(subOrRoot);//e.g. tetraphosphane, disiloxane
			processIndicatedHydrogens(subOrRoot);
			processStereochemistry(subOrRoot);
			processInfixes(subOrRoot);
			processSuffixPrefixes(subOrRoot);
			processLambdaConvention(subOrRoot);
		}
		List<Element> groups =  XOMTools.getDescendantElementsWithTagName(moleculeEl, GROUP_EL);

		
		/* Converts open/close bracket elements to bracket elements and
		 *  places the elements inbetween within the newly created bracket */
		while(findAndStructureBrackets(substituentsAndRoot));
		
		processHydroCarbonRings(moleculeEl);
		for (Element group : groups) {
			processRings(group);//processes cyclo, von baeyer and spiro tokens
			handleGroupIrregularities(group);//handles benzyl, diethylene glycol, phenanthrone and other awkward bits of nomenclature
		}
		handleSuffixIrregularities(XOMTools.getDescendantElementsWithTagName(moleculeEl, SUFFIX_EL));//handles quinone -->dioxo

		addOmittedSpaces(moleculeEl);//e.g. change ethylmethyl ether to ethyl methyl ether
	}

	/**
	 * Resolves common ambiguities e.g. tetradeca being 4x10carbon chain rather than 14carbon chain
	 * @param elem
	 * @throws PostProcessingException
	 */
	private void resolveAmbiguities(Element elem) throws PostProcessingException {
		List<Element> multipliers = XOMTools.getDescendantElementsWithTagName(elem, MULTIPLIER_EL);
		for (Element apparentMultiplier : multipliers) {
			Element nextEl = (Element)XOMTools.getNextSibling(apparentMultiplier);
			if(nextEl !=null && nextEl.getLocalName().equals(GROUP_EL) && nextEl.getAttributeValue(TYPE_ATR).equals(CHAIN_TYPE_VAL)){//detects ambiguous use of things like tetradeca
				String multiplierAndGroup =apparentMultiplier.getValue() + nextEl.getValue();
				HashMap<String, HashMap<Character, Token>> tokenDict =resourceManager.tokenDict;
				HashMap<Character,Token> tokenMap = tokenDict.get(multiplierAndGroup);
				if (tokenMap !=null){
					Element isThisALocant =(Element)XOMTools.getPreviousSibling(apparentMultiplier);
					if (isThisALocant == null ||
							!isThisALocant.getLocalName().equals(LOCANT_EL) ||
							matchComma.split(isThisALocant.getValue()).length != Integer.parseInt(apparentMultiplier.getAttributeValue(VALUE_ATR))){
						throw new PostProcessingException(multiplierAndGroup +" should not have been lexed as two tokens!");
					}
				}
			}

			if (nextEl !=null && nextEl.getLocalName().equals(HYDROCARBONFUSEDRINGSYSTEM_EL)&& nextEl.getValue().equals("phen")){//deals with tetra phen yl vs tetraphen yl
				Element possibleSuffix = (Element) XOMTools.getNextSibling(nextEl);
				if (possibleSuffix!=null){//null if not used as substituent
					String multiplierAndGroup =apparentMultiplier.getValue() + nextEl.getValue();
					if (possibleSuffix.getValue().equals("yl")){
						throw new PostProcessingException(multiplierAndGroup +" should not have been lexed as one token!");
					}
					Element isThisALocant =(Element)XOMTools.getPreviousSibling(apparentMultiplier);
					if (isThisALocant != null && isThisALocant.getLocalName().equals("locant") && matchComma.split(isThisALocant.getValue()).length == Integer.parseInt(apparentMultiplier.getAttributeValue(VALUE_ATR))){
						throw new PostProcessingException(multiplierAndGroup +" should not have been lexed as one token!");
					}
				}
			}
			if (Integer.parseInt(apparentMultiplier.getAttributeValue(VALUE_ATR))>4 && !apparentMultiplier.getValue().endsWith("a")){//disambiguate pent oxy and the like. Assume it means pentanoxy rather than 5 oxys
				if (nextEl !=null && nextEl.getLocalName().equals(GROUP_EL)&& matchInlineSuffixesThatAreAlsoGroups.matcher(nextEl.getValue()).matches()){
					throw new PostProcessingException(apparentMultiplier.getValue() + nextEl.getValue() +" should have been lexed as [alkane stem, inline suffix], not [multiplier, group]!");
				}
			}
		
		}

		List<Element> fusions = XOMTools.getDescendantElementsWithTagName(elem, FUSION_EL);
		for (Element fusion : fusions) {
			String fusionText = fusion.getValue();
			if (matchNumberLocantsOnlyFusionBracket.matcher(fusionText).matches()){
				Element nextGroup =(Element) XOMTools.getNextSibling(fusion, GROUP_EL);
				if (nextGroup !=null && nextGroup.getAttributeValue(SUBTYPE_ATR).equals(HANTZSCHWIDMAN_SUBTYPE_VAL)){
					int heteroCount = 0;
					int multiplierValue = 1;
					int hwRingSize = Integer.parseInt(nextGroup.getAttributeValue(VALUE_ATR));
					Element currentElem = (Element) XOMTools.getNextSibling(fusion);
					while(currentElem != null && !currentElem.getLocalName().equals(GROUP_EL)){
						if(currentElem.getLocalName().equals(HETEROATOM_EL)) {
							heteroCount+=multiplierValue;
							multiplierValue =1;
						} else if (currentElem.getLocalName().equals(MULTIPLIER_EL)){
							multiplierValue = Integer.parseInt(currentElem.getAttributeValue(VALUE_ATR));
						}
						currentElem = (Element)XOMTools.getNextSibling(currentElem);
					}
					String[] locants = matchComma.split(fusionText.substring(1, fusionText.length()-1));
					if (locants.length == heteroCount){
						boolean foundLocantNotInHwSystem =false;
						for (String locant : locants) {
							if (hwRingSize > Integer.parseInt(locant.substring(0,1))){
								foundLocantNotInHwSystem =true;
							}
						}
						if (!foundLocantNotInHwSystem){
						throw new PostProcessingException("This fusion bracket is in fact more likely to be a description of the locants of a HW ring");
						}
					}
				}
			}
		}
	}
	
	
	/**
	 * Removes hyphens from the end of locants if present
	 * Looks for locants of the form number-letter and converts them to letternumber
	 * e.g. 1-N becomes N1. 1-N is the IUPAC 2004 recommendation, N1 is the previous recommendation
	 * @param subOrRoot
	 * @throws PostProcessingException 
	 */
	private void processLocants(Element subOrRoot) throws PostProcessingException {
		Elements locants = subOrRoot.getChildElements(LOCANT_EL);
		for (int i = 0; i < locants.size(); i++) {
			Element locant =locants.get(i);
			
			String[] individualLocantText = matchComma.split(StringTools.removeDashIfPresent(locant.getValue()));
			for (int j = 0; j < individualLocantText.length; j++) {
				String locantText =individualLocantText[j];
				if (locantText.contains("-")){//this checks should avoid having to do the regex match in all cases as locants shouldn't contain -
					Matcher m= matchIUPAC2004ElementLocant.matcher(locantText);
					if (m.matches()){
						individualLocantText[j] = m.group(2) +m.group(1);
					}
					else{
						throw new PostProcessingException("Unexpected hyphen in locantText");
					}
				}
				else if (matchNonDigit.matcher(locantText).lookingAt()){
					Matcher m =  matchSuperscriptedLocant.matcher(locantText);//remove indications of superscript as the fact a locant is superscripted can be determined from context e.g. N~1~ ->N1
					if (m.matches()){
						individualLocantText[j] = m.group(1) +m.group(2);
					}
					else if (locantText.length()>=3 && Character.isLetter(locantText.charAt(1)) && Character.isLetter(locantText.charAt(2))){//convert greeks to lower case
						individualLocantText[j] = locantText.toLowerCase();
					}
				}
			}
			XOMTools.setTextChild(locant, StringTools.arrayToString(individualLocantText, ","));
		}
	}

	/**
	 * Applies the traditional alkane modifiers: iso, tert, sec, neo by modifying the alkane chain's SMILES
	 * 
	 * @param subOrRoot
	 * @throws PostProcessingException 
	 */
	private void processAlkaneStemModifications(Element subOrRoot) throws PostProcessingException {
		Elements alkaneStemModifiers = subOrRoot.getChildElements(ALKANESTEMMODIFIER_EL);
		for(int i=0;i<alkaneStemModifiers.size();i++) {
			Element alkaneStemModifier =alkaneStemModifiers.get(i);
			Element alkane = (Element) XOMTools.getNextSibling(alkaneStemModifier);
			if (alkane ==null || alkane.getAttribute(VALTYPE_ATR)==null || !alkane.getAttributeValue(VALTYPE_ATR).equals(CHAIN_VALTYPE_VAL)
					|| alkane.getAttribute(SUBTYPE_ATR)==null || !alkane.getAttributeValue(SUBTYPE_ATR).equals(ALKANESTEM_SUBTYPE_VAL)){
				throw new PostProcessingException("OPSIN Bug: AlkaneStem not found after alkaneStemModifier");
			}
			String type;
			if (alkaneStemModifier.getAttribute(VALUE_ATR)!=null){
				type = alkaneStemModifier.getAttributeValue(VALUE_ATR);//identified by token;
			}
			else{
				if (alkaneStemModifier.getValue().equals("n-")){
					type="normal";
				}
				else if (alkaneStemModifier.getValue().equals("i-")){
					type="iso";
				}
				else if (alkaneStemModifier.getValue().equals("s-")){
					type="sec";
				}
				else{
					throw new PostProcessingException("Unrecognised alkaneStem modifier");
				}
			}
			alkaneStemModifier.detach();
			if (type.equals("normal")){
				continue;//do nothing
			}
			int chainLength = Integer.parseInt(alkane.getAttributeValue(VALUE_ATR));
			boolean suffixPresent = subOrRoot.getChildElements(SUFFIX_EL).size() > 0;
			String smiles;
			if (type.equals("tert")){
				if (chainLength <4){
					throw new PostProcessingException("ChainLength to small for tert modifier, required minLength 4. Found: " +chainLength);
				}
				if (chainLength >=8){
					throw new PostProcessingException("Interpretation of tert on an alkane chain of length: " + chainLength +" is ambiguous");
				}
				smiles ="C(C)(C)C" + StringTools.multiplyString("C", chainLength-4);
			}
			else if (type.equals("iso")){
				if (chainLength <3){
					throw new PostProcessingException("ChainLength to small for iso modifier, required minLength 3. Found: " +chainLength);
				}
				if (chainLength==3 && !suffixPresent){
					throw new PostProcessingException("iso has no meaning without a suffix on an alkane chain of length 3");
				}
				smiles =StringTools.multiplyString("C", chainLength-3) +"C(C)C";
			}
			else if (type.equals("sec")){
				if (chainLength <3){
					throw new PostProcessingException("ChainLength to small for sec modifier, required minLength 3. Found: " +chainLength);
				}
				if (!suffixPresent){
					throw new PostProcessingException("sec has no meaning without a suffix on an alkane chain");
				}
				smiles ="C(C)C" + StringTools.multiplyString("C", chainLength-3);
			}
			else if (type.equals("neo")){
				if (chainLength <5){
					throw new PostProcessingException("ChainLength to small for neo modifier, required minLength 5. Found: " +chainLength);
				}
				smiles = StringTools.multiplyString("C", chainLength-5) + "CC(C)(C)C";
			}
			else{
				throw new PostProcessingException("Unrecognised alkaneStem modifier");
			}
			alkane.getAttribute(VALTYPE_ATR).setValue(SMILES_VALTYPE_VAL);
			alkane.getAttribute(VALUE_ATR).setValue(smiles);
			alkane.removeAttribute(alkane.getAttribute(USABLEASJOINER_ATR));
			alkane.addAttribute(new Attribute(LABELS_ATR, NONE_LABELS_VAL));
		}
	}

	/**Form heterogeneous hydrides/substituents
	 * These are chains of one heteroatom or alternating heteroatoms and are expressed using SMILES
	 * They are typically treated in an analagous way to alkanes
	 * @param elem The root/substituents
	 */
	private void processHeterogenousHydrides(Element elem)  {
		Elements multipliers = elem.getChildElements(MULTIPLIER_EL);
		for(int i=0;i<multipliers.size();i++) {
			Element m = multipliers.get(i);
			if (m.getAttributeValue(TYPE_ATR).equals(GROUP_TYPE_VAL)){
				continue;
			}
			int mvalue = Integer.parseInt(m.getAttributeValue(VALUE_ATR));
			Element multipliedElem = (Element)XOMTools.getNextSibling(m);

			if(multipliedElem.getLocalName().equals(GROUP_EL) &&
					multipliedElem.getAttribute(SUBTYPE_ATR)!=null &&
					multipliedElem.getAttributeValue(SUBTYPE_ATR).equals(HETEROSTEM_SUBTYPE_VAL)) {
				
				Element possiblyALocant = (Element)XOMTools.getPreviousSibling(m);//detect rare case where multiplier does not mean form a chain of heteroatoms e.g. something like 1,2-disulfanylpropane
				if(possiblyALocant !=null && possiblyALocant.getLocalName().equals(LOCANT_EL)&& mvalue==matchComma.split(possiblyALocant.getValue()).length){
					Element suffix =(Element) XOMTools.getNextSibling(multipliedElem, SUFFIX_EL);
					if (suffix !=null && suffix.getAttributeValue(TYPE_ATR).equals(INLINE_TYPE_VAL)){
						Element possibleMultiplier = (Element) XOMTools.getPreviousSibling(suffix);
						if (!possibleMultiplier.getLocalName().equals(MULTIPLIER_EL)){//NOT something like 3,3'-diselane-1,2-diyl
							continue;
						}
					}
				}
				
				//chain of heteroatoms
				String smiles=multipliedElem.getAttributeValue(VALUE_ATR);
				multipliedElem.getAttribute(VALUE_ATR).setValue(StringTools.multiplyString(smiles, mvalue));
				m.detach();
			}
		}
		Elements groups = elem.getChildElements(GROUP_EL);

		if (groups.size()==0){
			for(int i=0;i<multipliers.size();i++) {
				Element m = multipliers.get(i);
				Element multipliedElem = (Element)XOMTools.getNextSibling(m);
				if(multipliedElem.getLocalName().equals(HETEROATOM_EL)){
					Element possiblyAnotherHeteroAtom = (Element)XOMTools.getNextSibling(multipliedElem);
					if (possiblyAnotherHeteroAtom !=null && possiblyAnotherHeteroAtom.getLocalName().equals(HETEROATOM_EL)){
						Element possiblyAnUnsaturator = XOMTools.getNextSiblingIgnoringCertainElements(possiblyAnotherHeteroAtom, new String[]{LOCANT_EL, MULTIPLIER_EL});//typically ane but can be ene or yne e.g. triphosphaza-1,3-diene
						if (possiblyAnUnsaturator !=null && possiblyAnUnsaturator.getLocalName().equals(UNSATURATOR_EL)){
							//chain of alternating heteroatoms
							int mvalue = Integer.parseInt(m.getAttributeValue(VALUE_ATR));
							String smiles="";
							Element possiblyARingFormingEl = (Element)XOMTools.getPreviousSibling(m);
							boolean heteroatomChainWillFormARing =false;
							if (possiblyARingFormingEl!=null && (possiblyARingFormingEl.getLocalName().equals(CYCLO_EL) || possiblyARingFormingEl.getLocalName().equals(VONBAEYER_EL) || possiblyARingFormingEl.getLocalName().equals(SPIRO_EL))){
								heteroatomChainWillFormARing=true;
								//will be cyclised later.
								//FIXME sort based on order in HW system (also add check to HW stuff that the heteroatoms in that are in the correct order so that incorrectly ordered systems may be rejected.
								for (int j = 0; j < mvalue; j++) {
									smiles+=possiblyAnotherHeteroAtom.getAttributeValue(VALUE_ATR);
									smiles+=multipliedElem.getAttributeValue(VALUE_ATR);
								}
							}
							else{
								for (int j = 0; j < mvalue -1; j++) {
									smiles+=multipliedElem.getAttributeValue(VALUE_ATR);
									smiles+=possiblyAnotherHeteroAtom.getAttributeValue(VALUE_ATR);
								}
								smiles+=multipliedElem.getAttributeValue(VALUE_ATR);
							}
							multipliedElem.detach();

							Element addedGroup=new Element(GROUP_EL);
							addedGroup.addAttribute(new Attribute(VALUE_ATR, smiles));
							addedGroup.addAttribute(new Attribute(VALTYPE_ATR, SMILES_VALTYPE_VAL));
							addedGroup.addAttribute(new Attribute(TYPE_ATR, CHAIN_VALTYPE_VAL));
							addedGroup.addAttribute(new Attribute(SUBTYPE_ATR, HETEROSTEM_SUBTYPE_VAL));
							if (!heteroatomChainWillFormARing){
								addedGroup.addAttribute(new Attribute(USABLEASJOINER_ATR, "yes"));
							}
							addedGroup.appendChild(smiles);
							XOMTools.insertAfter(possiblyAnotherHeteroAtom, addedGroup);

							possiblyAnotherHeteroAtom.detach();
							m.detach();
						}
					}
				}
			}
		}
	}

	/** Handle 1H- in 1H-pyrrole etc.
	 *
	 * @param elem The substituent/root to looks for indicated hydrogens in.
	 */
	private void processIndicatedHydrogens(Element elem) {
		Elements hydrogens = elem.getChildElements(HYDROGEN_EL);
		for(int i=0;i<hydrogens.size();i++) {
			Element hydrogen = hydrogens.get(i);
			String txt = hydrogen.getValue();
			String[] hydrogenLocants =matchComma.split(txt);
            for (String hydrogenLocant : hydrogenLocants) {//TODO this should have an else throw exception clause? maybe should employ removedashifpresent
                if (hydrogenLocant.endsWith("H-")) {
                    Element newHydrogenElement = new Element(HYDROGEN_EL);
                    newHydrogenElement.addAttribute(new Attribute(LOCANT_ATR, hydrogenLocant.substring(0, hydrogenLocant.length() - 2)));
                    XOMTools.insertAfter(hydrogen, newHydrogenElement);
                } else if (hydrogenLocant.endsWith("H")) {
                    Element newHydrogenElement = new Element(HYDROGEN_EL);
                    newHydrogenElement.addAttribute(new Attribute(LOCANT_ATR, hydrogenLocant.substring(0, hydrogenLocant.length() - 1)));
                    XOMTools.insertAfter(hydrogen, newHydrogenElement);
                }
            }
			hydrogen.detach();
		}
	}

	/** Handles stereoChemistry (R/Z/E/Z/cis/trans)
	 *  Will assign a locant to a stereoChemistry element if one was specified/available
	 *
	 * @param elem The substituent/root to looks for stereoChemistry in.
	 * @throws PostProcessingException
	 */
	private void processStereochemistry(Element elem) throws PostProcessingException {
		Elements stereoChemistryElements = elem.getChildElements(STEREOCHEMISTRY_EL);
		for(int i=0;i<stereoChemistryElements.size();i++) {
			Element stereoChemistryElement = stereoChemistryElements.get(i);
			if (stereoChemistryElement.getAttributeValue(TYPE_ATR).equals(STEREOCHEMISTRYBRACKET_TYPE_VAL)){
				String txt = stereoChemistryElement.getValue();
				if (txt.startsWith("rel-")){
					txt = txt.substring(4);
				}
				Matcher starMatcher = matchStar.matcher(txt);
				txt = starMatcher.replaceAll("");
				if (!txt.startsWith("rac-")){
					txt =txt.substring(1, txt.length()-1);//remove opening and closing bracket.
					String[] stereoChemistryDescriptors = matchComma.split(txt);
                    for (String stereoChemistryDescriptor : stereoChemistryDescriptors) {
                        if (stereoChemistryDescriptor.length() > 1) {
                            Matcher m = matchStereochemistry.matcher(stereoChemistryDescriptor);
                            if (m.matches()){
                            	if (!m.group(2).equals("RS") && !m.group(2).equals("SR")){
	                                Element stereoChemEl = new Element(STEREOCHEMISTRY_EL);
	                                stereoChemEl.addAttribute(new Attribute(LOCANT_ATR, m.group(1)));
	                                stereoChemEl.addAttribute(new Attribute(VALUE_ATR, m.group(2).toUpperCase()));
	                                stereoChemEl.appendChild(stereoChemistryDescriptor);
	                                XOMTools.insertAfter(stereoChemistryElement, stereoChemEl);
	                                if (matchRS.matcher(m.group(2)).matches()) {
	                                    stereoChemEl.addAttribute(new Attribute(TYPE_ATR, R_OR_S_TYPE_VAL));
	                                } else {
	                                    stereoChemEl.addAttribute(new Attribute(TYPE_ATR, E_OR_Z_TYPE_VAL));
	                                }
                            	}
                            } else {
                                throw new PostProcessingException("Malformed stereochemistry element: " + stereoChemistryElement.getValue());
                            }
                        } else {
                            Element stereoChemEl = new Element(STEREOCHEMISTRY_EL);
                            stereoChemEl.addAttribute(new Attribute(VALUE_ATR, stereoChemistryDescriptor.toUpperCase()));
                            stereoChemEl.appendChild(stereoChemistryDescriptor);
                            XOMTools.insertAfter(stereoChemistryElement, stereoChemEl);
                            if (matchRS.matcher(stereoChemistryDescriptor).matches()) {
                                stereoChemEl.addAttribute(new Attribute(TYPE_ATR, R_OR_S_TYPE_VAL));
                            } else if (matchEZ.matcher(stereoChemistryDescriptor).matches()) {
                                stereoChemEl.addAttribute(new Attribute(TYPE_ATR, E_OR_Z_TYPE_VAL));
                            } else {
                                throw new PostProcessingException("Malformed stereochemistry element: " + stereoChemistryElement.getValue());
                            }
                        }
                    }
				}
				stereoChemistryElement.detach();
			}
			else if (stereoChemistryElement.getAttributeValue(TYPE_ATR).equals(CISORTRANS_TYPE_VAL)){//assign a locant if one is directly before the cis/trans
				Element possibleLocant = (Element) XOMTools.getPrevious(stereoChemistryElement);
				if (possibleLocant !=null && possibleLocant.getLocalName().equals(LOCANT_EL) && matchComma.split(possibleLocant.getValue()).length==1){
					stereoChemistryElement.addAttribute(new Attribute(LOCANT_ATR, possibleLocant.getValue()));
					possibleLocant.detach();
				}
			}
		}
	}

	/**
	 * Looks for "suffixPrefix" and assigns their value them as an attribute of an adjacent suffix
	 * @param subOrRoot
	 * @throws PostProcessingException
	 */
	private void processSuffixPrefixes(Element subOrRoot) throws PostProcessingException {
		List<Element> suffixPrefixes =  XOMTools.getChildElementsWithTagName(subOrRoot, SUFFIXPREFIX_EL);
		for (Element suffixPrefix : suffixPrefixes) {
			Element suffix = (Element) XOMTools.getNextSibling(suffixPrefix);
			if (suffix==null || ! suffix.getLocalName().equals(SUFFIX_EL)){
				throw new PostProcessingException("OPSIN bug: suffix not found after suffixPrefix: " + suffixPrefix.getValue());
			}
			suffix.addAttribute(new Attribute(SUFFIXPREFIX_ATR, suffixPrefix.getAttributeValue(VALUE_ATR)));
			suffixPrefix.detach();
		}
	}

	/**
	 * Looks for infixes and assigns them to the next suffix using a semicolon delimited infix attribute
	 * If the infix/suffix block has been bracketed e.g (dithioate) then the infix is multiplied out
	 * If preceded by a suffixPrefix e.g. sulfono infixes are also multiplied out
	 * If a multiplier is present and neither of these cases are met then it is ambiguous as to whether the multiplier is referring to the infix or the infixed suffix
	 * This ambiguity is resolved in processInfixFunctionalReplacementNomenclature by looking at the structure of the suffix to be modified
	 * @param subOrRoot
	 * @throws PostProcessingException
	 */
	private void processInfixes(Element subOrRoot) throws PostProcessingException {
		List<Element> infixes = XOMTools.getChildElementsWithTagName(subOrRoot, INFIX_EL);
		for (Element infix : infixes) {
			Element suffix = XOMTools.getNextSiblingIgnoringCertainElements(infix, new String[]{INFIX_EL, SUFFIXPREFIX_EL});
			if (suffix ==null || !suffix.getLocalName().equals(SUFFIX_EL)){
				throw new PostProcessingException("No suffix found next next to infix: "+ infix.getValue());
			}
			List<String> currentInfixInformation;
			if (suffix.getAttribute(INFIX_ATR)==null){
				suffix.addAttribute(new Attribute(INFIX_ATR, ""));
				currentInfixInformation = new ArrayList<String>();
			}
			else{
				currentInfixInformation = StringTools.arrayToList(matchSemiColon.split(suffix.getAttributeValue(INFIX_ATR)));
			}
			String infixValue =infix.getAttributeValue(VALUE_ATR);
			currentInfixInformation.add(infixValue);
			Element possibleMultiplier = (Element) XOMTools.getPreviousSibling(infix);
			Element possibleBracket;
			boolean suffixPrefixPresent =false;
			if (possibleMultiplier.getLocalName().equals(MULTIPLIER_EL)){
				Element possibleSuffixPrefix = XOMTools.getPreviousSiblingIgnoringCertainElements(infix, new String[]{MULTIPLIER_EL, INFIX_EL});
				if (possibleSuffixPrefix!=null && possibleSuffixPrefix.getLocalName().equals(SUFFIXPREFIX_EL)){
					suffixPrefixPresent =true;
				}
				possibleBracket  = (Element) XOMTools.getPreviousSibling(possibleMultiplier);
			}
			else{
				possibleBracket=possibleMultiplier;
				possibleMultiplier=null;
				infix.detach();
			}
			if (possibleBracket.getLocalName().equals(STRUCTURALOPENBRACKET_EL)){
				Element bracket = (Element) XOMTools.getNextSibling(suffix);
				if (!bracket.getLocalName().equals(STRUCTURALCLOSEBRACKET_EL)){
					throw new PostProcessingException("Matching closing bracket not found around infix/suffix block");
				}
				if (possibleMultiplier!=null){
					int multiplierVal = Integer.parseInt(possibleMultiplier.getAttributeValue(VALUE_ATR));
					for (int i = 1; i < multiplierVal; i++) {
						currentInfixInformation.add(infixValue);
					}
					possibleMultiplier.detach();
					infix.detach();
				}
				possibleBracket.detach();
				bracket.detach();
			}
			else if (possibleMultiplier!=null && suffixPrefixPresent){
				int multiplierVal = Integer.parseInt(possibleMultiplier.getAttributeValue(VALUE_ATR));
				for (int i = 1; i < multiplierVal; i++) {
					currentInfixInformation.add(infixValue);
				}
				possibleMultiplier.detach();
				infix.detach();
			}
			suffix.getAttribute(INFIX_ATR).setValue(StringTools.stringListToString(currentInfixInformation, ";"));
		}
	}

	/**
	 * Identifies lambdaConvention elements.
	 * The elementsValue is expected to be a comma seperated lambda values and 0 or more locants. Where a lambda value has the following form:
	 * optional locant, the word lambda and then a number which is the valency specified (with possibly some attempt to indicate this number is superscripted)
	 * If the element is followed by heteroatoms (possibly multiplied) they are multiplied and the locant/lambda assigned to them
	 * Otherwise a new lambdaConvention element is created with the valency specified by the lambda convention taking the attribute "lambda"
	 * In the case where heteroatoms belong to a fused ring system a new lambdaConvention element is also created. The original locants are retained in the benzo specific fused ring nomenclature:
	 * 2H-5lambda^5-phosphinino[3,2-b]pyran --> 2H 5lambda^5 phosphinino[3,2-b]pyran  BUT
	 * 1lambda^4,5-Benzodithiepin  --> 1lambda^4 1,5-Benzodithiepin
	 * @param subOrRoot
	 * @throws PostProcessingException
	 */
	private void processLambdaConvention(Element subOrRoot) throws PostProcessingException {
		List<Element> lambdaConventionEls = XOMTools.getChildElementsWithTagName(subOrRoot, LAMBDACONVENTION_EL);
		boolean fusedRingPresent = false;
		if (lambdaConventionEls.size()>0){
			if (subOrRoot.getChildElements(GROUP_EL).size()>1){
				fusedRingPresent = true;
			}
		}
		for (Element lambdaConventionEl : lambdaConventionEls) {
			boolean frontLocantsExpected =false;//Is the lambdaConvention el followed by benz/benzo of a fused ring system (these have front locants which correspond to the final fused rings numbering) or by a polycylicspiro system
			String[] lambdaValues = matchComma.split(StringTools.removeDashIfPresent(lambdaConventionEl.getValue()));
			Element possibleHeteroatomOrMultiplier = (Element) XOMTools.getNextSibling(lambdaConventionEl);
			int heteroCount = 0;
			int multiplierValue = 1;
			while(possibleHeteroatomOrMultiplier != null){
				if(possibleHeteroatomOrMultiplier.getLocalName().equals(HETEROATOM_EL)) {
					heteroCount+=multiplierValue;
					multiplierValue =1;
				} else if (possibleHeteroatomOrMultiplier.getLocalName().equals(MULTIPLIER_EL)){
					multiplierValue = Integer.parseInt(possibleHeteroatomOrMultiplier.getAttributeValue(VALUE_ATR));
				}
				else{
					break;
				}
				possibleHeteroatomOrMultiplier = (Element)XOMTools.getNextSibling(possibleHeteroatomOrMultiplier);
			}
			boolean assignLambdasToHeteroAtoms =false;
			if (lambdaValues.length==heteroCount){//heteroatom and number of locants +lambdas must match
				if (fusedRingPresent && possibleHeteroatomOrMultiplier!=null && possibleHeteroatomOrMultiplier.getLocalName().equals(GROUP_EL) && possibleHeteroatomOrMultiplier.getAttributeValue(SUBTYPE_ATR).equals(HANTZSCHWIDMAN_SUBTYPE_VAL)){
					//You must not set the locants of a HW system which forms a component of a fused ring system. The locant specified corresponds to the complete fused ring system.
				}
				else{
					assignLambdasToHeteroAtoms =true;
				}
			}
			else if(heteroCount==0 && XOMTools.getNextSibling(lambdaConventionEl).equals(possibleHeteroatomOrMultiplier) &&
					 possibleHeteroatomOrMultiplier!=null &&
						(fusedRingPresent && possibleHeteroatomOrMultiplier.getLocalName().equals(GROUP_EL) &&
						(possibleHeteroatomOrMultiplier.getValue().equals("benzo") || possibleHeteroatomOrMultiplier.getValue().equals("benz"))
						&& !((Element)XOMTools.getNextSibling(possibleHeteroatomOrMultiplier)).getLocalName().equals(FUSION_EL)) ||
						(possibleHeteroatomOrMultiplier.getLocalName().equals(POLYCYCLICSPIRO_EL))){
				frontLocantsExpected = true;//a benzo fused ring e.g. 1lambda4,3-benzothiazole or a poly cyclic spiro system
			}
			List<Element> heteroAtoms = new ArrayList<Element>();//contains the heteroatoms to apply the lambda values too. Can be empty if the values are applied to a group directly rather than to a heteroatom
			if (assignLambdasToHeteroAtoms){//populate heteroAtoms, multiplied heteroatoms are multiplied out
				Element multiplier = null;
				Element heteroatomOrMultiplier = (Element) XOMTools.getNextSibling(lambdaConventionEl);
				while(heteroatomOrMultiplier != null){
					if(heteroatomOrMultiplier.getLocalName().equals(HETEROATOM_EL)) {
						heteroAtoms.add(heteroatomOrMultiplier);
						if (multiplier!=null){
							for (int i = 1; i < Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR)); i++) {
								Element newHeteroAtom = new Element(heteroatomOrMultiplier);
								XOMTools.insertBefore(heteroatomOrMultiplier, newHeteroAtom);
								heteroAtoms.add(newHeteroAtom);
							}
							multiplier.detach();
							multiplier=null;
						}
					} else if (heteroatomOrMultiplier.getLocalName().equals(MULTIPLIER_EL)){
						if (multiplier !=null){
							break;
						}
						else{
							multiplier = heteroatomOrMultiplier;
						}
					}
					else{
						break;
					}
					heteroatomOrMultiplier = (Element)XOMTools.getNextSibling(heteroatomOrMultiplier);
				}
			}

			for (int i = 0; i < lambdaValues.length; i++) {//assign all the lambdas to heteroatoms or to newly created lambdaConvention elements
				String lambdaValue = lambdaValues[i];
				Matcher m = matchLambdaConvention.matcher(lambdaValue);
				if (m.matches()){//a lambda
					Attribute valencyChange = new Attribute(LAMBDA_ATR, m.group(2));
					Attribute locantAtr = null;
					if (m.group(1)!=null){
						locantAtr = new Attribute(LOCANT_ATR, m.group(1));
					}
					if (frontLocantsExpected){
						if (m.group(1)==null){
							throw new PostProcessingException("Locant not found for lambda convention before a benzo fused ring system");
						}
						lambdaValues[i] = m.group(1);
					}
					if (assignLambdasToHeteroAtoms){
						Element heteroAtom = heteroAtoms.get(i);
						heteroAtom.addAttribute(valencyChange);
						if (locantAtr!=null){
							heteroAtom.addAttribute(locantAtr);
						}
					}
					else{
						Element newLambda = new Element(LAMBDACONVENTION_EL);
						newLambda.addAttribute(valencyChange);
						if (locantAtr!=null){
							newLambda.addAttribute(locantAtr);
						}
						XOMTools.insertBefore(lambdaConventionEl, newLambda);
					}
				}
				else{//just a locant e.g 1,3lambda5
					if (!assignLambdasToHeteroAtoms){
						if (!frontLocantsExpected){
							throw new PostProcessingException("Lambda convention not specified for locant: " + lambdaValue);
						}
					}
					else{
						Element heteroAtom = heteroAtoms.get(i);
						heteroAtom.addAttribute(new Attribute(LOCANT_ATR, lambdaValue));
					}
				}
			}
			if (!frontLocantsExpected){
				lambdaConventionEl.detach();
			}
			else{
				lambdaConventionEl.setLocalName(LOCANT_EL);
				XOMTools.setTextChild(lambdaConventionEl, StringTools.arrayToString(lambdaValues, ","));
			}
		}
	}

	/**Finds matching open and close brackets, and places the
	 * elements contained within in a big &lt;bracket&gt; element.
	 *
	 * @param substituentsAndRoot: The substituent/root elements at the current level of the tree
	 * @return Whether the method did something, and so needs to be called again.
	 * @throws PostProcessingException
	 */
	private boolean findAndStructureBrackets(List<Element> substituentsAndRoot) throws PostProcessingException {
		int blevel = 0;
		Element openBracket = null;
		Element closeBracket = null;
		for (Element sub : substituentsAndRoot) {
			Elements children = sub.getChildElements();
			for(int i=0; i<children.size(); i++) {
				Element child = children.get(i);
				if(child.getLocalName().equals(OPENBRACKET_EL)) {
					if(openBracket == null) {
						openBracket = child;
					}
					blevel++;
				} else if (child.getLocalName().equals(CLOSEBRACKET_EL)) {
					blevel--;
					if(blevel == 0) {
						closeBracket = child;
						Element bracket = structureBrackets(openBracket, closeBracket);
						while(findAndStructureBrackets(XOMTools.getDescendantElementsWithTagName(bracket, SUBSTITUENT_EL)));
						return true;
					}
				}
			}
		}
		if (blevel != 0){
			throw new PostProcessingException("Brackets do not match!");
		}
		return false;
	}

	/**Places the elements in substituents containing/between an open and close bracket
	 * in a &lt;bracket&gt; tag.
	 *
	 * @param openBracket The open bracket element
	 * @param closeBracket The close bracket element
	 * @return The bracket element thus created.
	 */
	private Element structureBrackets(Element openBracket, Element closeBracket) {
		Element bracket = new Element(BRACKET_EL);
		XOMTools.insertBefore(openBracket.getParent(), bracket);
		/* Pick up everything in the substituent before the bracket*/
		while(!openBracket.getParent().getChild(0).equals(openBracket)) {
			Node n = openBracket.getParent().getChild(0);
			n.detach();
			bracket.appendChild(n);
		}
		/* Pick up all nodes from the one with the open bracket,
		 * to the one with the close bracket, inclusive.
		 */
		Node currentNode = openBracket.getParent();
		while(!currentNode.equals(closeBracket.getParent())) {
			Node nextNode = XOMTools.getNextSibling(currentNode);
			currentNode.detach();
			bracket.appendChild(currentNode);
			currentNode = nextNode;
		}
		currentNode.detach();
		bracket.appendChild(currentNode);
		/* Pick up nodes after the close bracket */
		currentNode = XOMTools.getNextSibling(closeBracket);
		while(currentNode != null) {
			Node nextNode = XOMTools.getNextSibling(currentNode);
			currentNode.detach();
			bracket.appendChild(currentNode);
			currentNode = nextNode;
		}
		openBracket.detach();
		closeBracket.detach();
	
		return bracket;
	}

	/**Looks for annulen/polyacene/polyaphene/polyalene/polyphenylene/polynaphthylene/polyhelicene tags and replaces them with a group with appropriate SMILES.
	 * @param elem The element to look for tags in
	 * @throws PostProcessingException
	 */
	private void processHydroCarbonRings(Element elem) throws PostProcessingException {
		List<Element> annulens = XOMTools.getDescendantElementsWithTagName(elem, ANNULEN_EL);
		for (Element annulen : annulens) {
			String annulenValue =annulen.getValue();
	        Matcher match = matchAnnulene.matcher(annulenValue);
	        match.matches();
	        if (match.groupCount() !=1){
	        	throw new PostProcessingException("Invalid annulen tag");
	        }

	        int annulenSize=Integer.valueOf(match.group(1));
	        if (annulenSize <3){
	        	throw new PostProcessingException("Invalid annulen tag");
	        }

			//build [annulenSize]annulene ring as SMILES
			String SMILES = "c1" +StringTools.multiplyString("c", annulenSize -1);
			SMILES += "1";

			Element group =new Element(GROUP_EL);
			group.addAttribute(new Attribute(VALUE_ATR, SMILES));
			group.addAttribute(new Attribute(VALTYPE_ATR, SMILES_VALTYPE_VAL));
			group.addAttribute(new Attribute(TYPE_ATR, RING_TYPE_VAL));
			group.addAttribute(new Attribute(SUBTYPE_ATR, ARYLGROUP_SUBTYPE_VAL));
			group.appendChild(annulenValue);
			annulen.getParent().replaceChild(annulen, group);
		}

		List<Element> hydrocarbonFRSystems = XOMTools.getDescendantElementsWithTagName(elem, HYDROCARBONFUSEDRINGSYSTEM_EL);
		for (Element hydrocarbonFRSystem : hydrocarbonFRSystems) {
			Element multiplier = (Element)XOMTools.getPreviousSibling(hydrocarbonFRSystem);
			if(multiplier != null && multiplier.getLocalName().equals(MULTIPLIER_EL)) {
				int multiplierValue =Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR));
				String classOfHydrocarbonFRSystem =hydrocarbonFRSystem.getAttributeValue(VALUE_ATR);
				Element newGroup =new Element(GROUP_EL);
				String SMILES="";
				if (classOfHydrocarbonFRSystem.equals("polyacene")){
					if (multiplierValue <=3){
						throw new PostProcessingException("Invalid polyacene");
					}
					SMILES= "c1ccc";
					for (int j = 2; j <= multiplierValue; j++) {
						SMILES+="c"+ringClosure(j);
						SMILES+="c";
					}
					SMILES+= "ccc";
					for (int j = multiplierValue; j >2; j--) {
						SMILES+="c"+ringClosure(j);
						SMILES+="c";
					}
					SMILES+="c12";
				}else if (classOfHydrocarbonFRSystem.equals("polyaphene")){
					if (multiplierValue <=3){
						throw new PostProcessingException("Invalid polyaphene");
					}
					SMILES= "c1ccc";

					int ringsAbovePlane;
					int ringsOnPlane;
					int ringOpeningCounter=2;
					if (multiplierValue %2==0){
						ringsAbovePlane =(multiplierValue-2)/2;
						ringsOnPlane =ringsAbovePlane +1;
					}
					else{
						ringsAbovePlane =(multiplierValue-1)/2;
						ringsOnPlane =ringsAbovePlane;
					}

					for (int j = 1; j <= ringsAbovePlane; j++) {
						SMILES+="c"+ringClosure(ringOpeningCounter++);
						SMILES+="c";
					}

					for (int j = 1; j <= ringsOnPlane; j++) {
						SMILES+="c";
						SMILES+="c"+ringClosure(ringOpeningCounter++);
					}
					SMILES+="ccc";
					ringOpeningCounter--;
					for (int j = 1; j <= ringsOnPlane; j++) {
						SMILES+="c";
						SMILES+="c"+ringClosure(ringOpeningCounter--);
					}
					for (int j = 1; j < ringsAbovePlane; j++) {
						SMILES+="c"+ringClosure(ringOpeningCounter--);
						SMILES+="c";
					}

					SMILES+="c12";
				} else if (classOfHydrocarbonFRSystem.equals("polyalene")){
					if (multiplierValue <5){
						throw new PostProcessingException("Invalid polyalene");
					}
					SMILES= "c1";
					for (int j = 3; j < multiplierValue; j++) {
						SMILES+="c";
					}
					SMILES+= "c2";
					for (int j = 3; j <= multiplierValue; j++) {
						SMILES+="c";
					}
					SMILES+="c12";
				} else if (classOfHydrocarbonFRSystem.equals("polyphenylene")){
					if (multiplierValue <2){
						throw new PostProcessingException("Invalid polyphenylene");
					}
					SMILES= "c1cccc2";
					for (int j = 1; j < multiplierValue; j++) {
						SMILES+="c3ccccc3";
					}
					SMILES+= "c12";
				} else if (classOfHydrocarbonFRSystem.equals("polynaphthylene")){
					if (multiplierValue <3){
						throw new PostProcessingException("Invalid polynaphthylene");
					}
					SMILES= "c1cccc2cc3";
					for (int j = 1; j < multiplierValue; j++) {
						SMILES+="c4cc5ccccc5cc4";
					}
					SMILES+= "c3cc12";
				} else if (classOfHydrocarbonFRSystem.equals("polyhelicene")){
					if (multiplierValue <6){
						throw new PostProcessingException("Invalid polyhelicene");
					}
					SMILES= "c1c";
					int ringOpeningCounter=2;
					for (int j = 1; j < multiplierValue; j++) {
						SMILES+="ccc" + ringClosure(ringOpeningCounter++);
					}
					SMILES+= "cccc";
					ringOpeningCounter--;
					for (int j = 2; j < multiplierValue; j++) {
						SMILES+="c" + ringClosure(ringOpeningCounter--);
					}
					SMILES+= "c12";
				}

				else{
					throw new PostProcessingException("Unknown semi-trivially named hydrocarbon fused ring system");
				}

				newGroup.addAttribute(new Attribute(VALUE_ATR, SMILES));
				newGroup.addAttribute(new Attribute(VALTYPE_ATR, SMILES_VALTYPE_VAL));
				newGroup.addAttribute(new Attribute(LABELS_ATR, FUSEDRING_LABELS_VAL));
				newGroup.addAttribute(new Attribute(TYPE_ATR, RING_TYPE_VAL));
				newGroup.addAttribute(new Attribute(SUBTYPE_ATR, HYDROCARBONFUSEDRINGSYSTEM_EL));
				newGroup.appendChild(multiplier.getValue() + hydrocarbonFRSystem.getValue());
				hydrocarbonFRSystem.getParent().replaceChild(hydrocarbonFRSystem, newGroup);
				multiplier.detach();
			}
			else{
				throw new PostProcessingException("Invalid semi-trivially named hydrocarbon fused ring system");
			}
		}
	}

	/**Looks (multiplier)cyclo/spiro/cyclo tags before chain
	 * and replaces them with a group with appropriate SMILES
	 * Note that only simple spiro tags are handled at this stage i.e. not dispiro
	 * @param group A group which is potentially a chain
	 * @throws PostProcessingException
	 */
	private void processRings(Element group) throws PostProcessingException {
		Element previous = (Element)XOMTools.getPreviousSibling(group);
		if(previous != null) {
			if(previous.getLocalName().equals(SPIRO_EL)){
				processSpiroSystem(group, previous);
			} else if(previous.getLocalName().equals(VONBAEYER_EL)) {
				processVonBaeyerSystem(group, previous);
			}
			else if(previous.getLocalName().equals(CYCLO_EL)) {
				if (!group.getAttributeValue(SUBTYPE_ATR).equals(HETEROSTEM_SUBTYPE_VAL)){
					int chainlen = Integer.parseInt(group.getAttributeValue(VALUE_ATR));
					if (chainlen < 3){
						throw new PostProcessingException("Alkane chain too small to create a cyclo alkane: " + chainlen);
					}
					String smiles = "C1" + StringTools.multiplyString("C", chainlen - 1) + "1";
					group.addAttribute(new Attribute(VALUE_ATR, smiles));
					group.addAttribute(new Attribute(VALTYPE_ATR, SMILES_VALTYPE_VAL));
				}
				else{
					String smiles=group.getAttributeValue(VALUE_ATR);
					smiles+="1";
					if (smiles.charAt(0)=='['){
						int closeBracketIndex = smiles.indexOf(']');
						smiles= smiles.substring(0, closeBracketIndex +1) +"1" + smiles.substring(closeBracketIndex +1);
					}
					else{
						if (Character.getType(smiles.charAt(1)) == Character.LOWERCASE_LETTER){//element is 2 letters long
							smiles= smiles.substring(0,2) +"1" + smiles.substring(2);
						}
						else{
							smiles= smiles.substring(0,1) +"1" + smiles.substring(1);
						}
					}
					group.getAttribute(VALUE_ATR).setValue(smiles);
				}
				group.getAttribute(TYPE_ATR).setValue(RING_TYPE_VAL);
				previous.detach();
			}
		}
	}

	/**
	 * Processes a spiro descriptor element.
	 * This modifies the provided chainGroup into the spiro system by replacing the value of the chain group with appropriate SMILES
	 * @param chainGroup
	 * @param spiroEl
	 * @throws PostProcessingException 
	 * @throws NumberFormatException 
	 */
	private void processSpiroSystem(Element chainGroup, Element spiroEl) throws NumberFormatException, PostProcessingException {
		int[][] spiroDescriptors = getSpiroDescriptors(StringTools.removeDashIfPresent(spiroEl.getValue()));

		Element multiplier =(Element)XOMTools.getPreviousSibling(spiroEl);
		int numberOfSpiros = 1;
		if (multiplier != null && multiplier.getLocalName().equals(MULTIPLIER_EL)){
			numberOfSpiros = Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR));
			multiplier.detach();
		}
		int numberOfCarbonInDescriptors =0;
		for (int[] spiroDescriptor : spiroDescriptors) {
			numberOfCarbonInDescriptors += spiroDescriptor[0];
		}
		numberOfCarbonInDescriptors += numberOfSpiros;
		if (numberOfCarbonInDescriptors != Integer.parseInt(chainGroup.getAttributeValue(VALUE_ATR))){
			throw new PostProcessingException("Disagreement between number of atoms in spiro descriptor: " + numberOfCarbonInDescriptors +" and number of atoms in chain: " + Integer.parseInt(chainGroup.getAttributeValue(VALUE_ATR)));
		}

		int numOfOpenedBrackets = 1;
		int curIndex = 2;
		String smiles = "C0" + StringTools.multiplyString("C", spiroDescriptors[0][0]) + "10(";

		// for those molecules where no superstrings compare prefix number with curIndex.
		for (int i=1; i< spiroDescriptors.length; i++) {
			if (spiroDescriptors[i][1] >= 0) {
				int ringOpeningPos = findIndexOfRingOpenings( smiles, spiroDescriptors[i][1] );
				String ringOpeningLabel = String.valueOf(smiles.charAt(ringOpeningPos));
				ringOpeningPos++;
				if (ringOpeningLabel.equals("%")){
					while (smiles.charAt(ringOpeningPos)>='0' && smiles.charAt(ringOpeningPos)<='9' && ringOpeningPos < smiles.length()) {
						ringOpeningLabel += smiles.charAt(ringOpeningPos);
						ringOpeningPos++;
					}
				}
				if (smiles.indexOf("C" + ringOpeningLabel, ringOpeningPos)>=0) {
					// this ring opening has already been closed
					// i.e. this atom connects more than one ring in a spiro fusion
					
					// insert extra ring opening
					smiles = smiles.substring(0, ringOpeningPos) + ringClosure(curIndex) + smiles.substring(ringOpeningPos);

					// add ring in new brackets
					smiles += "(" + StringTools.multiplyString("C", spiroDescriptors[i][0]) + ringClosure(curIndex) + ")";
					curIndex++;
				}
				else {
					smiles += StringTools.multiplyString("C", spiroDescriptors[i][0]) + ringOpeningLabel + ")";
				}
			}
			else if (numOfOpenedBrackets >= numberOfSpiros) {
				smiles += StringTools.multiplyString("C", spiroDescriptors[i][0]);

				// take the number before bracket as index for smiles
				// we can open more brackets, this considered in prev if
				curIndex--;
				smiles += ringClosure(curIndex) + ")";

				// from here start to decrease index for the following
			}
			else {
				smiles += StringTools.multiplyString("C", spiroDescriptors[i][0]);
				smiles += "C" + ringClosure(curIndex++) + "(";
				numOfOpenedBrackets++;
			}
		}
		chainGroup.addAttribute(new Attribute(VALUE_ATR, smiles));
		chainGroup.addAttribute(new Attribute(VALTYPE_ATR, SMILES_VALTYPE_VAL));
		chainGroup.getAttribute(TYPE_ATR).setValue(RING_TYPE_VAL);
		spiroEl.detach();
	}

	/**
	 * If the integer given is > 9 return %ringClosure else just returns ringClosure
	 * @param ringClosure
	 * @return
	 */
	private String ringClosure(int ringClosure) {
		if (ringClosure >9){
			return "%" +Integer.toString(ringClosure);
		}
		else{
			return Integer.toString(ringClosure);
		}
	}

	/**
	 * Prepares spiro string for processing
	 * @param text - string with spiro e.g. spiro[2.2]
	 * @return array with number of carbons in each group and associated index of spiro atom
	 */
	private int[][] getSpiroDescriptors(String text) {
		if (text.indexOf("-")==5){
			text= text.substring(7, text.length()-1);//cut off spiro-[ and terminal ]
		}
		else{
			text= text.substring(6, text.length()-1);//cut off spiro[ and terminal ]
		}
		
		String[] spiroDescriptorStrings = matchDot.split(text);
	
		int[][] spiroDescriptors = new int[spiroDescriptorStrings.length][2]; // array of descriptors where number of elements and super string present
	
		for (int i=0; i < spiroDescriptorStrings.length; i++) {
			String[] elements = matchNonDigit.split(spiroDescriptorStrings[i]);
			if (elements.length >1) {//a "superscripted" number is present
				spiroDescriptors[i][0] = Integer.parseInt(elements[0]);
				String superScriptedNumber ="";
				for (int j = 1; j < elements.length; j++){//may be more than one non digit as there are many ways of indicating superscripts
					superScriptedNumber += elements[j];
				}
				spiroDescriptors[i][1] = Integer.parseInt(superScriptedNumber);
			}
			else {
				spiroDescriptors[i][0] = Integer.parseInt(spiroDescriptorStrings[i]);
				spiroDescriptors[i][1] = -1;
			}
		}
	
		return spiroDescriptors;
	}

	/**
	 * Finds the the carbon atom with the given locant in the provided SMILES
	 * Returns the next index which is expected to correspond to the atom's ring opening/s
	 * @param smiles string to search in
	 * @param locant locant of the atom in given structure
	 * @return index of ring openings
	 * @throws PostProcessingException 
	 */
	private Integer findIndexOfRingOpenings(String smiles, int locant) throws PostProcessingException{
		int count = 0;
		int pos = -1;
		for (int i=0; i<smiles.length(); i++){
			if (smiles.charAt(i) == 'C') {
				count++;
			}
			if (count==locant) {
				pos=i;
				break;
			}
		}
		if (pos == -1){
			throw new PostProcessingException("Unable to find atom corresponding to number indicated by superscript in spiro descriptor");
		}
		pos++;
		return pos;
	}

	/**
	 * Given an element corresponding to an alkane or other systematic chain and the preceding vonBaeyerBracket element:
	 * Generates the SMILES of the von baeyer system and assigns this to the chain Element
	 * Checks are done on the von baeyer multiplier and chain length
	 * The multiplier and vonBaeyerBracket are detached
	 * @param chainEl
	 * @param vonBaeyerBracketEl
	 * @throws PostProcessingException
	 */
	private void processVonBaeyerSystem(Element chainEl, Element vonBaeyerBracketEl) throws PostProcessingException {
		String vonBaeyerBracket = StringTools.removeDashIfPresent(vonBaeyerBracketEl.getValue());
		Element multiplier =(Element)XOMTools.getPreviousSibling(vonBaeyerBracketEl);
		int numberOfRings=Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR));
		multiplier.detach();

		int alkylChainLength;
		LinkedList<String> elementSymbolArray = new LinkedList<String>();
		if (chainEl.getAttributeValue(VALTYPE_ATR).equals(CHAIN_VALTYPE_VAL)){
			alkylChainLength=Integer.parseInt(chainEl.getAttributeValue(VALUE_ATR));
			for (int i = 0; i < alkylChainLength; i++) {
				elementSymbolArray.add("C");
			}
		}
		else if (chainEl.getAttributeValue(VALTYPE_ATR).equals(SMILES_VALTYPE_VAL)){
			String smiles =chainEl.getAttributeValue(VALUE_ATR);
			char[] smilesArray =smiles.toCharArray();
			for (int i = 0; i < smilesArray.length; i++) {//only able to interpret the SMILES that should be in an unmodified unbranched chain
				char currentChar =smilesArray[i];
				if (currentChar == '['){
					if ( smilesArray[i +2]==']'){
						elementSymbolArray.add("[" +String.valueOf(smilesArray[i+1]) +"]");
						i=i+2;
					}
					else{
						elementSymbolArray.add("[" + String.valueOf(smilesArray[i+1]) +String.valueOf(smilesArray[i+2]) +"]");
						i=i+3;
					}
				}
				else{
					elementSymbolArray.add(String.valueOf(currentChar));
				}
			}
			alkylChainLength=elementSymbolArray.size();
		}
		else{
			throw new PostProcessingException("unexpected group valType: " + chainEl.getAttributeValue(VALTYPE_ATR));
		}


		int totalLengthOfBridges=0;
		int bridgeLabelsUsed=3;//start labelling from 3 upwards
		//3 and 4 will be the atoms on each end of one secondary bridge, 5 and 6 for the next etc.

		ArrayList<HashMap<String, Integer>> bridges = new ArrayList<HashMap<String, Integer>>();
		HashMap<Integer, ArrayList<Integer>> bridgeLocations = new HashMap<Integer, ArrayList<Integer>>(alkylChainLength);
		if (vonBaeyerBracket.indexOf("-")==5){
			vonBaeyerBracket = vonBaeyerBracket.substring(7, vonBaeyerBracket.length()-1);//cut off cyclo-[ and terminal ]
		}
		else{
			vonBaeyerBracket = vonBaeyerBracket.substring(6, vonBaeyerBracket.length()-1);//cut off cyclo[ and terminal ]
		}
		Matcher m = matchVonBaeyer.matcher(vonBaeyerBracket);
		while(m.find()) {
			String[] lengthOfBridgeArray= matchComma.split(m.group(0));
			HashMap<String, Integer> bridge = new HashMap<String, Integer>();
			int bridgeLength =0;
			if (lengthOfBridgeArray.length > 1){//this is a secondary bridge (chain start/end locations have been specified)

				String coordinatesStr1;
				String coordinatesStr2 =lengthOfBridgeArray[1].replaceAll("\\D", "");
				String[] tempArray = lengthOfBridgeArray[0].split("\\D+");

				if (tempArray.length ==1){
					//there is some ambiguity as it has not been made obvious which number/s are supposed superscripted to be the superscripted locant
					//so we assume that it is more likely that it will be referring to an atom of label >10
					//rather than a secondary bridge of length > 10
					char[] tempCharArray = lengthOfBridgeArray[0].toCharArray();
					if (tempCharArray.length ==2){
						bridgeLength= Character.getNumericValue(tempCharArray[0]);
						coordinatesStr1= Character.toString(tempCharArray[1]);
					}
					else if (tempCharArray.length ==3){
						bridgeLength= Character.getNumericValue(tempCharArray[0]);
						coordinatesStr1=Character.toString(tempCharArray[1]) +Character.toString(tempCharArray[2]);
					}
					else if (tempCharArray.length ==4){
						bridgeLength = Integer.parseInt(Character.toString(tempCharArray[0]) +Character.toString(tempCharArray[1]));
						coordinatesStr1 = Character.toString(tempCharArray[2]) +Character.toString(tempCharArray[3]);
					}
					else{
						throw new PostProcessingException("Unsupported Von Baeyer locant description: " + m.group(0) );
					}
				}
				else{//bracket or other delimiter detected, no ambiguity!
					bridgeLength= Integer.parseInt(tempArray[0]);
					coordinatesStr1= tempArray[1];
				}

				bridge.put("Bridge Length", bridgeLength );
				int coordinates1=Integer.parseInt(coordinatesStr1);
				int coordinates2=Integer.parseInt(coordinatesStr2);
				if (coordinates1 > alkylChainLength || coordinates2 > alkylChainLength){
					throw new PostProcessingException("Indicated bridge position is not on chain: " +coordinates1 +"," +coordinates2);
				}
				if (coordinates2>coordinates1){//makes sure that bridges are built from highest coord to lowest
					int swap =coordinates1;
					coordinates1=coordinates2;
					coordinates2=swap;
				}
				if (bridgeLocations.get(coordinates1)==null){
					bridgeLocations.put(coordinates1, new ArrayList<Integer>());
				}
				if (bridgeLocations.get(coordinates2)==null){
					bridgeLocations.put(coordinates2, new ArrayList<Integer>());
				}
				bridgeLocations.get(coordinates1).add(bridgeLabelsUsed);
				bridge.put("AtomId_Larger_Label", bridgeLabelsUsed);
				bridgeLabelsUsed++;
				if (bridgeLength==0){//0 length bridge, hence want atoms with the same labels so they can join together without a bridge
					bridgeLocations.get(coordinates2).add(bridgeLabelsUsed -1);
					bridge.put("AtomId_Smaller_Label", bridgeLabelsUsed -1);
				}
				else{
					bridgeLocations.get(coordinates2).add(bridgeLabelsUsed);
					bridge.put("AtomId_Smaller_Label", bridgeLabelsUsed);
				}
				bridgeLabelsUsed++;

				bridge.put("AtomId_Larger", coordinates1);
				bridge.put("AtomId_Smaller", coordinates2);
			}
			else{
				bridgeLength= Integer.parseInt(lengthOfBridgeArray[0]);
				bridge.put("Bridge Length", bridgeLength);
			}
			totalLengthOfBridges += bridgeLength;
			bridges.add(bridge);
		}
		if (totalLengthOfBridges + 2 !=alkylChainLength ){
			throw new PostProcessingException("Disagreement between lengths of bridges and alkyl chain length");
		}
		if (numberOfRings +1 != bridges.size()){
			throw new PostProcessingException("Disagreement between number of rings and number of bridges");
		}

		String SMILES="";
		int atomCounter=1;
		int bridgeCounter=1;
		//add standard bridges
		for (HashMap<String, Integer> bridge : bridges) {
			if (bridgeCounter==1){
				SMILES += elementSymbolArray.removeFirst() +"1";
				if (bridgeLocations.get(atomCounter)!=null){
					for (Integer bridgeAtomLabel : bridgeLocations.get(atomCounter)) {
						SMILES += ringClosure(bridgeAtomLabel);
					}
				}
				SMILES += "(";
			}
			int bridgeLength =bridge.get("Bridge Length");

			for (int i = 0; i < bridgeLength; i++) {
				atomCounter++;
				SMILES +=elementSymbolArray.removeFirst();
				if (bridgeLocations.get(atomCounter)!=null){
					for (Integer bridgeAtomLabel : bridgeLocations.get(atomCounter)) {
						SMILES +=ringClosure(bridgeAtomLabel);
					}
				}
			}
			if (bridgeCounter==1){
				atomCounter++;
				SMILES += elementSymbolArray.removeFirst() +"2";
				if (bridgeLocations.get(atomCounter)!=null){
					for (Integer bridgeAtomLabel : bridgeLocations.get(atomCounter)) {
						SMILES +=ringClosure(bridgeAtomLabel);
					}
				}
			}
			if (bridgeCounter==2){
				SMILES += "1)";
			}
			if (bridgeCounter==3){
				SMILES += "2";
			}
			bridgeCounter++;
			if (bridgeCounter >3){break;}
		}

		//create list of secondary bridges that need to be added
		//0 length bridges and the 3 main bridges are dropped
		ArrayList<HashMap<String, Integer>> secondaryBridges = new ArrayList<HashMap<String, Integer>>();
		for (HashMap<String, Integer> bridge : bridges) {
			if(bridge.get("AtomId_Larger")!=null && bridge.get("Bridge Length")!=0){
				secondaryBridges.add(bridge);
			}
		}

		Comparator<HashMap<String, Integer>> sortBridges= new VonBaeyerSecondaryBridgeSort();
		Collections.sort(secondaryBridges, sortBridges);

		ArrayList<HashMap<String, Integer>> dependantSecondaryBridges;
		//add secondary bridges, recursively add dependent secondary bridges
		do{
			dependantSecondaryBridges = new ArrayList<HashMap<String, Integer>>();
			for (HashMap<String, Integer> bridge : secondaryBridges) {
				int bridgeLength =bridge.get("Bridge Length");
				if (bridge.get("AtomId_Larger") > atomCounter){
					dependantSecondaryBridges.add(bridge);
					continue;
				}
				SMILES+=".";
				for (int i = 0; i < bridgeLength; i++) {
					atomCounter++;
					SMILES +=elementSymbolArray.removeFirst();
					if (i==0){SMILES+=ringClosure(bridge.get("AtomId_Larger_Label"));}
					if (bridgeLocations.get(atomCounter)!=null){
						for (Integer bridgeAtomLabel : bridgeLocations.get(atomCounter)) {
							SMILES += ringClosure(bridgeAtomLabel);
						}
					}
				}
				SMILES+= ringClosure(bridge.get("AtomId_Smaller_Label"));
			}
			if (dependantSecondaryBridges.size() >0 && dependantSecondaryBridges.size()==secondaryBridges.size()){
				throw new PostProcessingException("Unable to resolve all dependant bridges!!!");
			}
			secondaryBridges=dependantSecondaryBridges;
		}
		while(dependantSecondaryBridges.size() > 0);

		chainEl.addAttribute(new Attribute(VALUE_ATR, SMILES));
		chainEl.addAttribute(new Attribute(VALTYPE_ATR, SMILES_VALTYPE_VAL));
		chainEl.getAttribute(TYPE_ATR).setValue(RING_TYPE_VAL);
		vonBaeyerBracketEl.detach();
	}

	/**Handles special cases in IUPAC nomenclature.
	 * Benzyl etc.
	 * @param group The group to look for irregularities in.
	 */
	private void handleGroupIrregularities(Element group) throws PostProcessingException {
		String groupValue =group.getValue();
		if(groupValue.equals("thiophen")) {//thiophenol is phenol with an O replaced with S not thiophene with a hydroxy
			Element possibleSuffix = (Element) XOMTools.getNextSibling(group);
			if (possibleSuffix !=null && possibleSuffix.getLocalName().equals(SUFFIX_EL)) {
				if (possibleSuffix.getValue().startsWith("ol")){
					throw new PostProcessingException("thiophenol has been incorrectly interpreted as thiophen, ol instead of thio, phenol");
				}
			}
		}
		else if (groupValue.equals("ethylene")) {
			Element previous = (Element)XOMTools.getPreviousSibling(group);
			if (previous!=null && previous.getLocalName().equals(MULTIPLIER_EL)){
				int multiplierValue = Integer.parseInt(previous.getAttributeValue(VALUE_ATR));
				Element possibleRoot =(Element) XOMTools.getNextSibling(group.getParent());
				if (possibleRoot==null && OpsinTools.getParentWordRule(group).getAttributeValue(WORDRULE_ATR).equals(WordRule.glycol.toString())){//e.g. dodecaethylene glycol
					String smiles ="CC";
					for (int i = 1; i < multiplierValue; i++) {
						smiles+="OCC";
					}
					group.getAttribute(OUTIDS_ATR).setValue("1," +Integer.toString(3*(multiplierValue-1) +2));
					group.getAttribute(VALUE_ATR).setValue(smiles);
					previous.detach();
				}
				else if (possibleRoot!=null && possibleRoot.getLocalName().equals(ROOT_EL)){
					Elements children = possibleRoot.getChildElements();
					if (children.size()==2){
						Element amineMultiplier =children.get(0);
						Element amine =children.get(1);
						if (amineMultiplier.getLocalName().equals(MULTIPLIER_EL) && amine.getValue().equals("amin")){//e.g. Triethylenetetramine
							if (Integer.parseInt(amineMultiplier.getAttributeValue(VALUE_ATR))!=multiplierValue +1){
								throw new PostProcessingException("Invalid polyethylene amine!");
							}
							String smiles ="";
							for (int i = 0; i < multiplierValue; i++) {
								smiles+="NCC";
							}
							smiles+="N";
							group.removeAttribute(group.getAttribute(OUTIDS_ATR));
							group.getAttribute(VALUE_ATR).setValue(smiles);
							previous.detach();
							possibleRoot.detach();
							((Element)group.getParent()).setLocalName(ROOT_EL);
						}
					}
				}
			}
		}
		else if (groupValue.equals("propylene")) {
			Element previous = (Element)XOMTools.getPreviousSibling(group);
			if (previous!=null && previous.getLocalName().equals(MULTIPLIER_EL)){
				int multiplierValue = Integer.parseInt(previous.getAttributeValue(VALUE_ATR));
				Element possibleRoot =(Element) XOMTools.getNextSibling(group.getParent());
				if (possibleRoot==null && OpsinTools.getParentWordRule(group).getAttributeValue(WORDRULE_ATR).equals(WordRule.glycol.toString())){//e.g. dodecaethylene glycol
					String smiles ="CCC";
					for (int i = 1; i < multiplierValue; i++) {
						smiles+="OC(C)C";
					}
					group.getAttribute(OUTIDS_ATR).setValue("2," +Integer.toString(4*(multiplierValue-1) +3));
					group.getAttribute(VALUE_ATR).setValue(smiles);
					if (group.getAttribute(LABELS_ATR)!=null){
						group.getAttribute(LABELS_ATR).setValue(NONE_LABELS_VAL);
					}
					else{
						group.addAttribute(new Attribute(LABELS_ATR, NONE_LABELS_VAL));
					}
					previous.detach();
				}
			}
		}

		//anthrone, phenanthrone and xanthone have the one at position 9 by default
		else if (groupValue.equals("anthr") || groupValue.equals("phenanthr") || groupValue.equals("xanth")|| groupValue.equals("xanthen")) {
			Element possibleLocant = (Element) XOMTools.getPreviousSibling(group);
			if (possibleLocant==null || !possibleLocant.getLocalName().equals(LOCANT_EL)){//only need to give one a locant of 9 if no locant currently present
				Element possibleOne =(Element) XOMTools.getNextSibling(group);
				if (possibleOne!=null && possibleOne.getValue().equals("one")){
					Element newLocant =new Element(LOCANT_EL);
					newLocant.appendChild("9(10H)");//Rule C-315.2
					XOMTools.insertBefore(possibleOne, newLocant);
				}
			}
		}
		else if (groupValue.equals("phospho")){//is this the organic meaning (P(=O)=O) or biochemical meaning (P(=O)(O)O)
			Element substituent = (Element) group.getParent();
			Element nextSubstituent = (Element) XOMTools.getNextSibling(substituent);
			if (nextSubstituent !=null){
				Element nextGroup = nextSubstituent.getFirstChildElement(GROUP_EL);
				if (nextGroup !=null && (nextGroup.getAttributeValue(TYPE_ATR).equals(AMINOACID_TYPE_VAL)||BIOCHEMICAL_SUBTYPE_VAL.equals(nextGroup.getAttributeValue(SUBTYPE_ATR)))){
					group.getAttribute(VALUE_ATR).setValue("-P(=O)(O)O");
					group.addAttribute(new Attribute(USABLEASJOINER_ATR, "yes"));
				}
			}
			
		}
		else if (groupValue.equals("cyste")){//ambiguity between cysteine and cysteic acid
			if (group.getAttributeValue(SUBTYPE_ATR).equals(ENDININE_SUBTYPE_VAL)){//cysteine
				Element ine = (Element) XOMTools.getNextSibling(group);
				if (!ine.getAttributeValue(VALUE_ATR).equals("ine")){
					throw new PostProcessingException("This is a cysteic acid derivative, not a cysteine derivative");
				}
			}
		}
		else if (groupValue.equals("hydrogen")){
			if (XOMTools.getNextSibling(group.getParent())!=null){
				throw new PostProcessingException("Hydrogen is not meant as a substituent in this context!");
			}
		}
		else if (groupValue.equals("azo") || groupValue.equals("azoxy") || groupValue.equals("nno-azoxy") || groupValue.equals("non-azoxy") || groupValue.equals("onn-azoxy")){
			Element next = (Element) XOMTools.getNextSibling(group.getParent());
			if (next!=null && next.getLocalName().equals(ROOT_EL)){
				if (!(((Element)next.getChild(0)).getLocalName().equals(MULTIPLIER_EL))){
					List<Element> suffixes = XOMTools.getChildElementsWithTagName(next, SUFFIX_EL);
					if (suffixes.size()==0){//only case without locants is handled so far. suffixes only apply to one of the fragments rather than both!!!
						Element newMultiplier = new Element(MULTIPLIER_EL);
						newMultiplier.addAttribute(new Attribute(VALUE_ATR, "2"));
						next.insertChild(newMultiplier, 0);
					}
				}
			}
		}
	}
	
	/**
	 * Handles irregular suffixes. Quinone only currently
	 * @param suffixes
	 * @throws PostProcessingException 
	 */
	private void handleSuffixIrregularities(List<Element> suffixes) throws PostProcessingException {
		for (Element suffix : suffixes) {
			String suffixValue = suffix.getValue();
			if (suffixValue.equals("ic") || suffixValue.equals("ous")){
				Node next = XOMTools.getNext(suffix);
				if (next == null){
					throw new PostProcessingException("\"acid\" not found after " +suffixValue);
				}
			}
			// convert quinone to dione
			else if (suffixValue.equals("quinone")){
				suffix.removeAttribute(suffix.getAttribute(ADDITIONALVALUE_ATR));
				XOMTools.setTextChild(suffix, "one");
				Element multiplier = new Element(MULTIPLIER_EL);
				multiplier.addAttribute(new Attribute(VALUE_ATR, "2"));
				multiplier.appendChild("di");
				XOMTools.insertBefore(suffix, multiplier);
			}
		}
	}

	/**
	 * Moves substituents into new words if it appears that a space was omitted in the input name
	 * @param elem
	 */
	private void addOmittedSpaces(Element elem)  {
		List<Element> wordRules = XOMTools.getDescendantElementsWithTagName(elem, WORDRULE_EL);
		for (Element wordRule : wordRules) {
			if (WordRule.valueOf(wordRule.getAttributeValue(WORDRULE_ATR)) == WordRule.divalentFunctionalGroup){
				List<Element> substituentWords = XOMTools.getChildElementsWithTagNameAndAttribute(wordRule, WORD_EL, TYPE_ATR, SUBSTITUENT_TYPE_VAL);
				if (substituentWords.size()==1){//potentially been "wrongly" interpreted e.g. ethylmethyl ketone is more likely to mean ethyl methyl ketone
					Elements children  =substituentWords.get(0).getChildElements();
					if (children.size()==2){
						Element firstChildOfFirstSubstituent =(Element)children.get(0).getChild(0);
						//rule out correct usage e.g. diethyl ether and locanted substituents e.g. 2-methylpropyl ether
						if (!firstChildOfFirstSubstituent.getLocalName().equals(LOCANT_EL) && !firstChildOfFirstSubstituent.getLocalName().equals(MULTIPLIER_EL)){
							Element subToMove =children.get(1);
							subToMove.detach();
							Element newWord =new Element(WORD_EL);
							newWord.addAttribute(new Attribute(TYPE_ATR, SUBSTITUENT_TYPE_VAL));
							newWord.appendChild(subToMove);
							XOMTools.insertAfter(substituentWords.get(0), newWord);
						}
					}
				}
			}
		}
	}

}

