OPSIN - Open Parser for Systematic IUPAC Nomenclature
=====================================================
__Version 2.4.0 (see ReleaseNotes.txt for what's new in this version)__  
__Source code: <https://github.com/dan2097/opsin>__  
__Web interface and informational site: <http://opsin.ch.cam.ac.uk/>__  
__License: [MIT License](https://opensource.org/licenses/MIT)__  

OPSIN is a Java(1.6+) library for IUPAC name-to-structure conversion offering high recall and precision on organic chemical nomenclature.  
Supported outputs are SMILES, CML (Chemical Markup Language) and InChI (IUPAC International Chemical Identifier)

### Simple Usage Examples
#### Convert a chemical name to SMILES
`java -jar opsin-2.4.0-jar-with-dependencies.jar -osmi input.txt output.txt`  
where input.txt contains chemical name/s, one per line

    NameToStructure nts = NameToStructure.getInstance();
    String smiles = nts.parseToSmiles("acetonitrile");

#### Convert a chemical name to CML
`java -jar opsin-2.4.0-jar-with-dependencies.jar -ocml input.txt output.txt`  
where input.txt contains chemical name/s, one per line

    NameToStructure nts = NameToStructure.getInstance();
    String cml = nts.parseToCML("acetonitrile");

#### Convert a chemical name to StdInChI/StdInChIKey/InChI with FixedH 
`java -jar opsin-2.4.0-jar-with-dependencies.jar -ostdinchi input.txt output.txt`  
`java -jar opsin-2.4.0-jar-with-dependencies.jar -ostdinchikey input.txt output.txt`  
`java -jar opsin-2.4.0-jar-with-dependencies.jar -oinchi input.txt output.txt`  
where input.txt contains chemical name/s, one per line

    NameToInchi nti = new NameToInchi()
    String stdinchi = nti.parseToStdInchi("acetonitrile");
    String stdinchikey = nti.parseToStdInchiKey("acetonitrile");
    String inchi = nti.parseToInchi("acetonitrile");

NOTE: OPSIN's non-standard InChI includes an additional layer (FixedH) that indicates which tautomer the chemical name described. StdInChI aims to be tautomer independent.
### Advanced Usage
OPSIN 2.4.0 allows enabling of the following options:

* allowRadicals: Allows substituents to be interpretable e.g. allows interpretation of "ethyl"
* wildcardRadicals: If allowRadicals is enabled, this option uses atoms in the output to represent radicals: 'R' in CML and '*' in SMILES e.g. changes the output of ethyl from C[CH2] to CC\*
* detailedFailureAnalysis: Provides a potentially more accurate reason as to why a chemical name could not be parsed. This is done by parsing the chemical name from right to left. The trade-off for enabling this is slightly increased memory usage.
* allowAcidsWithoutAcid: Allows interpretation of acids without the word acid e.g. "acetic"
* allowUninterpretableStereo: Allows stereochemistry uninterpretable by OPSIN to be ignored (When used as a library the OpsinResult has a status of WARNING if stereochemistry was ignored)
* verbose: Enables debugging output\*

\*When used as a library this is done by modifying Log4J's logging level e.g. `Logger.getLogger("uk.ac.cam.ch.wwmm.opsin").setLevel(Level.DEBUG);`

The usage of these options on the command line is described in the command line's help dialog accessible via:
`java -jar opsin-2.4.0-jar-with-dependencies.jar -h`

These options may be controlled using the following code:

    NameToStructure nts = NameToStructure.getInstance();
    NameToStructureConfig ntsconfig = new NameToStructureConfig();
    //a new NameToStructureConfig starts as a copy of OPSIN's default configuration
    ntsconfig.setAllowRadicals(true);
    OpsinResult result = nts.parseChemicalName("acetonitrile", ntsconfig);
    String cml = result.getCml();
    String smiles = result.getSmiles();
    String stdinchi = NameToInchi.convertResultToStdInChI(result);

`result.getStatus()` may be checked to see if the conversion was successful.
If a structure was generated but OPSIN believes there may be a problem a status of WARNING is returned. Currently this may occur if the name appeared to be ambiguous or stereochemistry was ignored.
By default only optical rotation specification is ignored (this cannot be converted to stereo-configuration algorithmically).

Convenience methods like `result.nameAppearsToBeAmbiguous()` may be used to check the cause of the warning.

NOTE: (Std)InChI cannot be generated for polymers or radicals generated in combination with the wildcardRadicals option

### Availability
OPSIN is available as a standalone JAR from GitHub, <https://github.com/dan2097/opsin/releases>  
`opsin-2.4.0-jar-with-dependencies.jar` can be executed as a commandline application or added to the classpath for library usage.
OPSIN is also available from the Maven Central Repository for users of Apache Maven.  

If you are using Maven then add the following to your pom.xml:

    <dependency>
       <groupId>uk.ac.cam.ch.opsin</groupId>
       <artifactId>opsin-core</artifactId>
       <version>2.4.0</version>
    </dependency>

If you need just CML or SMILES output support

or

    <dependency>
       <groupId>uk.ac.cam.ch.opsin</groupId>
       <artifactId>opsin-inchi</artifactId>
       <version>2.4.0</version>
    </dependency>

  if you also need InChI output support.

#### Building from source
To build OPSIN from source, download Maven 3 and download OPSIN's source code.

Running `mvn package assembly:assembly` in the root of OPSIN's source will build the jar with dependencies

Running `mvn assembly:assembly` in the opsin-core folder will build the "excludingInChI-jar-with-dependencies"

### About OPSIN

The workings of OPSIN are more fully described in:

    Chemical Name to Structure: OPSIN, an Open Source Solution
    Daniel M. Lowe, Peter T. Corbett, Peter Murray-Rust, Robert C. Glen
    Journal of Chemical Information and Modeling 2011 51 (3), 739-753

If you use OPSIN in your work, then it would be great if you could cite us.

The following list broadly summarises what OPSIN can currently do and what will be worked on in the future.

#### Supported nomenclature includes:
* alkanes/alkenes/alkynes/heteroatom chains e.g. hexane, hex-1-ene, tetrasiloxane and their cyclic analogues e.g. cyclopropane
* All IUPAC 1993 recommended rings
* Trivial acids
* Hantzsch-Widman e.g. 1,3-oxazole
* Spiro systems
* All von Baeyer rings e.g. bicyclo[2.2.2]octane
* Hydro e.g. 2,3-dihydropyridine
* Indicated hydrogen e.g. 1H-benzoimidazole
* Heteroatom replacement
* Specification of charge e.g. ium/ide/ylium/uide
* Multiplicative nomenclature e.g. ethylenediaminetetraacetic acid
* Conjunctive nomenclature e.g. cyclohexaneethanol
* Fused ring systems e.g. imidazo[4,5-d]pyridine
* Ring assemblies e.g. biphenyl
* Most prefix and infix functional replacement nomenclature
* The following functional classes: acetals, acids, alcohols, amides, anhydrides, anilides, azetidides, azides, bromides, chlorides,
cyanates, cyanides, esters, di/tri/tetra esters, ethers, fluorides, fulminates, glycol ethers, glycols, hemiacetals, hemiketal,
hydrazides, hydrazones, hydrides, hydroperoxides, hydroxides, imides, iodides, isocyanates, isocyanides, isoselenocyanates, isothiocyanates,
ketals, ketones, lactams, lactims, lactones, mercaptans, morpholides, oxides, oximes, peroxides, piperazides, piperidides, pyrrolidides,
selenides, selenocyanates, selenoketones, selenolsselenosemicarbazones, selenones, selenoxides, selones, semicarbazones, sulfides, sulfones,
sulfoxides, sultams, sultims, sultines, sultones, tellurides, telluroketones, tellurones, tellurosemicarbazones, telluroxides, thiocyanates,
thioketones, thiols, thiosemicarbazones
* Greek letters
* Lambda convention
* Amino Acids and derivatives
* Structure-based polymer names e.g. poly(2,2'-diamino-5-hexadecylbiphenyl-3,3'-diyl)
* Bridge prefixes e.g. methano
* Specification of oxidation numbers and charge on elements
* Perhalogeno terms
* Subtractive prefixes: deoxy, dehydro, anhydro, demethyl, deamino
* Stoichiometry ratios and mixture indicators
* Nucleosides, (oligo)nucleotides and their esters
* Carbohydrate nomenclature
* Simple CAS names including inverted CAS names
* Steroids including alpha/beta stereochemistry
* Isotopic labelling
* E/Z/R/S stereochemistry
* cis/trans indicating relative stereochemistry on rings and as a synonym of E/Z

#### Currently UNsupported nomenclature includes:
* Other less common stereochemical terms
* Most alkaloids/terpenoids
* Natural product specific nomenclature operations

### Developers and Contributors
* Rich Apodaca
* Albina Asadulina
* Peter Corbett
* Daniel Lowe (Current maintainer)
* John Mayfield
* Peter Murray-Rust
* Noel O'Boyle
* Mark Williamson

Thanks also to the many users who have contributed through suggestions and bug reporting.

![YourKit Logo](https://www.yourkit.com/images/yklogo.png)

OPSIN's developers use YourKit to profile and optimise code.

YourKit supports open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of [YourKit Java Profiler](https://www.yourkit.com/java/profiler/index.jsp) and [YourKit .NET Profiler](https://www.yourkit.com/.net/profiler/index.jsp), innovative and intelligent tools for profiling Java and .NET applications.

Good Luck and let us know if you have problems, comments or suggestions!
Bugs may be reported on the project's [issue tracker](https://github.com/dan2097/opsin/issues).

[![Build Status](https://travis-ci.com/dan2097/opsin.svg?branch=master)](https://travis-ci.com/dan2097/opsin)
