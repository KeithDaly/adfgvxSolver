package adfgvx;

import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.log4j.Logger;

import utils.IntPair;
import utils.Utils;

/**
 * This class does all the analysis. It uses various algorithms to solve the
 * ADFGVX cipher. Some crucial decryption phases are processed in dedicated
 * classes.
 * 
 * @author Ben Ruijl
 * 
 * @see RowIdentifier
 * @see Pattern
 * @see tetragram
 */
public class Analysis {
    /** Logger. */
    private static final Logger LOG = Logger.getLogger(Analysis.class);

    /** Random number generator. */
    private final Random random;
    /** Pattern solver. */
    private final Pattern pattern;
    /** tetragram solver. */
    private final Tetragram tetragram;

    /** Number of correct decryptions. */
    private int correctAnalysis = 0;

    /**
     * Creates a new crypto analyzer.
     * 
     * @param pattern
     *            Pattern solver
     * @param tetragram
     *            tetragram solver
     */
    public Analysis(final Pattern pattern, final Tetragram tetragram) {
	this.pattern = pattern;
	this.tetragram = tetragram;

	random = new Random();
    }

    public String decrypt(final String encryptedText, final int keylength) {
	final Grid cipherGrid = new Grid(keylength);
	cipherGrid.add(encryptedText);

	// calculate frequencies
	final List<List<Character>> gridData = cipherGrid.getGrid();
	final List<TObjectIntHashMap<Character>> freqs = new ArrayList<TObjectIntHashMap<Character>>();

	for (int i = 0; i < gridData.size(); i++) {
	    freqs.add(new TObjectIntHashMap<Character>());
	    final TObjectIntHashMap<Character> freq = freqs.get(i);

	    for (final Character c : PolybiusSquare.keyName) {
		freq.put(c, 0);
	    }

	    final List<Character> col = gridData.get(i);

	    for (int j = 0; j < col.size(); j++) {
		final Character curKey = col.get(j);
		freq.put(curKey, freq.get(curKey) + 1);
	    }

	    LOG.info(freq);
	}

	LOG.info("After sorting:");

	final List<TObjectIntHashMap<Character>> col = new ArrayList<TObjectIntHashMap<Character>>();
	final List<TObjectIntHashMap<Character>> row = new ArrayList<TObjectIntHashMap<Character>>();

	for (int i = 0; i < freqs.size(); i++) {
	    if (i < freqs.size() / 2) {
		col.add(freqs.get(i));
	    } else {
		row.add(freqs.get(i));
	    }
	}

	RowIdentifier.findOptimalGrouping(col, row);

	StringBuffer groups = new StringBuffer("Grouped: ");
	for (int i = 0; i < freqs.size(); i++) {
	    for (int j = 0; j < col.size(); j++) {
		if (freqs.get(i).equals(col.get(j))) {
		    groups.append(i + " ");
		    break;
		}
	    }
	}

	LOG.info(groups);

	// match pattern
	final List<List<Character>> charCol = new ArrayList<List<Character>>();
	final List<List<Character>> charRow = new ArrayList<List<Character>>();

	for (int i = 0; i < gridData.size(); i++) {
	    if (col.contains(freqs.get(i))) {
		charCol.add(gridData.get(i));
	    } else {
		charRow.add(gridData.get(i));
	    }
	}

	pattern.findOptimalPatternDistribution(charCol, charRow);

	groups = new StringBuffer("Result: ");
	for (int i = 0; i < charCol.size(); i++) {
	    for (int j = 0; j < gridData.size(); j++) {
		if (charCol.get(i).equals(gridData.get(j))) {
		    groups.append(j + " ");
		}
	    }

	    for (int j = 0; j < gridData.size(); j++) {
		if (charRow.get(i).equals(gridData.get(j))) {
		    groups.append(j + " ");
		}
	    }
	}

	LOG.info(groups);

	final String monoSubText = PolybiusSquare.unFraction(charRow, charCol);

	LOG.info("IC: " + indexOfCoincidence(monoSubText));

	Map<Character, Character> bestAlphabet = new HashMap<Character, Character>();

	for (int i = 0; i < 10; i++) { // 10 restarts
	    float fitness = 0;

	    for (int j = 0; j < 6; j++) {
		final Map<Character, Character> newAlphabet = hillClimb(
			monoSubText, Encryption.randomAlphabet());

		final float newFitness = (float) tetragram.fitness(monoSubText,
			newAlphabet);
		if (newFitness > fitness) {
		    fitness = newFitness;
		    bestAlphabet = newAlphabet;
		    LOG.info("Attempt : "
			    + Encryption.transcribeCipherText(monoSubText,
				    bestAlphabet));
		}
	    }
	}

	String result = Encryption.transcribeCipherText(monoSubText,
		bestAlphabet);
	LOG.info("Best result:" + result);
	return result;
    }

    /**
     * Does a full analysis. This functions picks a random string from a text,
     * generates a random square and key and tries to decrypt the string. At
     * every step, it checks if the decryption is still correct.
     * 
     * @param text
     *            Source text
     * @param testLevel
     *            How far to go in the check. 0 stops at the matching, 2 at the
     *            key, 3 or more goes all the way.
     */
    public void doAnalysis(final String text, int testLevel) {
	final PolybiusSquare square = PolybiusSquare.generateRandomSquare();
	LOG.debug(square);

	/* Generate an even keylength between 4 and 10. */
	final int keyLength = 8;// random.nextInt(4) * 2 + 4;
	final List<Integer> key = Grid.generateRandomKey(keyLength);
	LOG.info("Key: " + key);

	// Shrink ciphertext. It should be a multiple of the key length
	final int timesKeyLength = 50;
	final int start = random.nextInt(text.length() - keyLength
		* timesKeyLength);
	final String cipherTextPiece = text.substring(start, start + keyLength
		* timesKeyLength);
	LOG.info("Plain text: " + cipherTextPiece);

	final String fractionedText = square.fraction(cipherTextPiece);
	final Grid grid = new Grid(keyLength);
	grid.add(fractionedText);

	final List<TObjectIntHashMap<Character>> oldFreqs = new ArrayList<TObjectIntHashMap<Character>>();
	for (int i = 0; i < grid.getGrid().size(); i++) {
	    final TObjectIntHashMap<Character> freq = new TObjectIntHashMap<Character>();

	    for (final Character c : PolybiusSquare.keyName) {
		freq.put(c, 0);
	    }

	    final List<Character> col = grid.getGrid().get(i);

	    for (int j = 0; j < col.size(); j++) {
		freq.increment(col.get(j));
	    }
	    
	    oldFreqs.add(freq);

	    LOG.info(freq);
	}

	grid.switchColumns(key);

	final String encryptedText = grid.encode();

	LOG.debug(encryptedText);

	LOG.info("--------- BEGINNING OF DECRYPTION");

	final Grid cipherGrid = new Grid(key.size());
	cipherGrid.add(encryptedText);

	// calculate frequencies
	final List<List<Character>> gridData = cipherGrid.getGrid();
	final List<TObjectIntHashMap<Character>> freqs = new ArrayList<TObjectIntHashMap<Character>>();

	for (int i = 0; i < gridData.size(); i++) {
	    freqs.add(new TObjectIntHashMap<Character>());
	    final TObjectIntHashMap<Character> freq = freqs.get(i);

	    for (final Character c : PolybiusSquare.keyName) {
		freq.put(c, 0);
	    }

	    final List<Character> col = gridData.get(i);

	    for (int j = 0; j < col.size(); j++) {
		freq.increment(col.get(j));
	    }
	}

	LOG.info("After sorting:");

	final List<TObjectIntHashMap<Character>> col = new ArrayList<TObjectIntHashMap<Character>>();
	final List<TObjectIntHashMap<Character>> row = new ArrayList<TObjectIntHashMap<Character>>();

	for (int i = 0; i < freqs.size(); i++) {
	    if (i < freqs.size() / 2) {
		col.add(freqs.get(i));
	    } else {
		row.add(freqs.get(i));
	    }
	}

	RowIdentifier.findOptimalGrouping(col, row);
	LOG.info(col);
	LOG.info(row);

	int correct = 0;
	int correctTrans = 0;
	for (int i = 0; i < oldFreqs.size(); i++) {
	    if (i % 2 == 0) {
		if (col.contains(oldFreqs.get(i))) {
		    correct++;
		} else {
		    correctTrans++;
		}
	    }
	    else {
		if (row.contains(oldFreqs.get(i))) {
		    correct++;
		} else {
		    correctTrans++;
		}
	    }
	}

	LOG.info("Correct identification of rows and cols: "
		+ Math.max(correct, correctTrans) + "/" + key.size());

	if (Math.max(correct, correctTrans) == key.size()) {
	    if (testLevel == 1) {
		correctAnalysis++;
		return;
	    }
	} else {
	    return;
	}

	// match pattern
	final List<List<Character>> charCol = new ArrayList<List<Character>>();
	final List<List<Character>> charRow = new ArrayList<List<Character>>();

	for (int i = 0; i < gridData.size(); i++) {
	    if (col.contains(freqs.get(i))) {
		charCol.add(gridData.get(i));
	    } else {
		charRow.add(gridData.get(i));
	    }
	}

	pattern.findOptimalPatternDistribution(charCol, charRow);
	
	StringBuffer groups = new StringBuffer("Result: ");
	for (int i = 0; i < charCol.size(); i++) {
	    for (int j = 0; j < gridData.size(); j++) {
		if (charCol.get(i).equals(gridData.get(j))) {
		    groups.append(j + " ");
		}
	    }

	    for (int j = 0; j < gridData.size(); j++) {
		if (charRow.get(i).equals(gridData.get(j))) {
		    groups.append(j + " ");
		}
	    }
	}

	LOG.info(groups);
	
	// see if it is correct
	correct = 0;
	for (int i = 0; i < key.size(); i++) {
	    if (i % 2 == 0 && charCol.get(i / 2).equals(gridData.get(key.get(i)))) {
		correct++;
	    }

	    if (i % 2 == 1 && charRow.get(i / 2).equals(gridData.get(key.get(i)))) {
		correct++;
	    }
	}

	correctTrans = 0;
	for (int i = 0; i < key.size(); i++) {
	    if (i % 2 == 1 && charCol.get(i / 2).equals(gridData.get(key.get(i)))) {
		correctTrans++;
	    }

	    if (i % 2 == 0 && charRow.get(i / 2).equals(gridData.get(key.get(i)))) {
		correctTrans++;
	    }
	}

	LOG.info("Correct transposition grid after pattern check: "
		+ Math.max(correct, correctTrans) + "/" + key.size());
	
	final String monoSubText = PolybiusSquare.unFraction(charRow, charCol);
	LOG.info("IOC: " + indexOfCoincidence(monoSubText));

	if (Math.max(correct, correctTrans) == key.size()) {
	    if (testLevel == 2) {
		correctAnalysis++;
		return;
	    }
	} else {
	    return;
	}

	// transposition grid is correct, now do mono sub solving
	float fitness = 0;
	Map<Character, Character> bestAlphabet = new HashMap<Character, Character>();
	for (int j = 0; j < 1; j++) {
	    final Map<Character, Character> newAlphabet = hillClimb(
		    monoSubText, Encryption.randomAlphabet());

	    final float newFitness = (float) tetragram.fitness(monoSubText,
		    newAlphabet);
	    if (newFitness > fitness) {
		fitness = newFitness;
		bestAlphabet = newAlphabet;
	    }
	}

	LOG.info("ANSWER: "
		+ Encryption.transcribeCipherText(monoSubText, bestAlphabet));
	correctAnalysis++;

	LOG.info("--------- END OF DECRYPTION");
    }

    /**
     * A monoalphabetic substitution solver using simmulated annealing.
     * 
     * @param cipherText
     *            Cipher text
     * @param alphabet
     *            Starting alphabet
     * @param initialTemperature
     *            Initial tempterature
     * @param a
     *            Temperature factor
     * @param n
     *            Number of iterations to find equilibrium
     * @return Best alphabet
     */
    public Map<Character, Character> simmulatedAnnealing(
	    final String cipherText, final Map<Character, Character> alphabet,
	    final double initialTemperature, final double a, final int n) {

	final Random r = new Random();

	final double absZero = 0.0000001;
	double fitness = tetragram.fitness(cipherText, alphabet);

	@SuppressWarnings("unchecked")
	final Entry<Character, Character>[] alphabetArray = alphabet.entrySet()
		.toArray(new Entry[0]);

	double temperature = initialTemperature;
	while (temperature > absZero) {
	    for (int k = 0; k < n; k++) {
		boolean done = false;
		final double oldFitness = fitness;

		for (int i = 0; i < alphabet.size() - 1; i++) {
		    if (!done) {
			for (int j = i + 1; j < alphabet.size(); j++) {
			    final Character tmp = alphabetArray[i].getValue();
			    alphabetArray[i].setValue(alphabetArray[j]
				    .getValue());
			    alphabetArray[j].setValue(tmp);

			    fitness = tetragram.fitness(cipherText, alphabet);
			    // LOG.info(fitness - oldFitness);

			    if (fitness > oldFitness
				    || Math.exp((fitness - oldFitness)
					    / temperature) > r.nextDouble()) {
				done = true;
				break;
			    } else {
				// swap back
				alphabetArray[j].setValue(alphabetArray[i]
					.getValue());
				alphabetArray[i].setValue(tmp);

			    }
			}
		    }
		}
	    }

	    temperature = temperature * a;
	}

	return alphabet;
    }

    /**
     * A monoalphabetic substitution solver using hill-climbing.
     * 
     * @param cipherText
     *            Cipher text
     * @param alphabet
     *            Starting alphabet
     * @return Best alphabet
     */
    public Map<Character, Character> hillClimb(final String cipherText,
	    final Map<Character, Character> alphabet) {

	@SuppressWarnings("unchecked")
	final Entry<Character, Character>[] alphabetArray = alphabet.entrySet()
		.toArray(new Entry[0]);

	boolean goAgain = true;
	double fitness = tetragram.fitness(cipherText, alphabet);

	while (goAgain) {
	    goAgain = false;
	    final double oldFitness = fitness;

	    for (int i = 0; i < alphabet.size() - 1; i++) {
		if (!goAgain) {
		    for (int j = i + 1; j < alphabet.size(); j++) {

			final Character tmp = alphabetArray[i].getValue();
			alphabetArray[i].setValue(alphabetArray[j].getValue());
			alphabetArray[j].setValue(tmp);

			fitness = tetragram.fitness(cipherText, alphabet);

			if (fitness > oldFitness) {
			    goAgain = true;
			    break;
			} else {
			    // swap back
			    alphabetArray[j].setValue(alphabetArray[i]
				    .getValue());
			    alphabetArray[i].setValue(tmp);
			}
		    }
		}
	    }
	}

	return alphabet;
    }

    /**
     * Guesses an alphabet based on letter frequencies.
     * 
     * @param text
     *            Cipher text
     * @return Guessed alphabet
     */
    public Map<Character, Character> guessAlphabet(final String text) {
	final String letterFreqs = "ETAOINHSRDLMUWYCFGPBVKXJQZ0123456789";
	final TObjectIntHashMap<Character> freq = new TObjectIntHashMap<Character>();

	for (int i = 0; i < text.length(); i++) {
	    if (freq.containsKey(text.charAt(i))) {
		freq.put(text.charAt(i), freq.get(text.charAt(i)) + 1);
	    } else {
		freq.put(text.charAt(i), 1);
	    }
	}

	final List<IntPair<Character>> list = new ArrayList<IntPair<Character>>(
		freq.size());

	freq.forEachEntry(new TObjectIntProcedure<Character>() {

	    @Override
	    public boolean execute(Character key, int value) {
		list.add(new IntPair<Character>(key, value));
		return true;
	    }
	});

	Collections.sort(list, new Comparator<IntPair<Character>>() {
	    @Override
	    public int compare(final IntPair<Character> o1,
		    final IntPair<Character> o2) {
		return o1.getSecond() - o2.getSecond();
	    }
	});

	final Map<Character, Character> alphabet = new HashMap<Character, Character>();

	for (int i = 0; i < list.size(); i++) {
	    alphabet.put(list.get(i).getFirst(), letterFreqs.charAt(i));
	}

	return alphabet;
    }

    /**
     * Does multiple test runs of the hill-climber and verifying its results. It
     * generates a random substitution from the source text.
     * 
     * @param text
     *            Source text
     */
    public void doHillclimbTestRun(final String text) {
	final Random r = new Random();
	final int textLength = 100;
	int correct = 0;

	for (int i = 0; i < 100; i++) {
	    final int start = r.nextInt(text.length() - textLength);

	    final String plainText = text.substring(start, start + textLength);
	    LOG.info("Plain text:\t\t" + plainText);

	    // encode
	    final Map<Character, Character> encryptionAlphabet = Encryption
		    .randomAlphabet();
	    final String encryptedText = Encryption.transcribeCipherText(
		    plainText, encryptionAlphabet);

	    double fitness = Double.MIN_VALUE;
	    Map<Character, Character> bestAlphabet = new HashMap<Character, Character>();
	    for (int j = 0; j < 10; j++) {
		final Map<Character, Character> newAlphabet = hillClimb(
			encryptedText, Encryption.randomAlphabet());

		final double newFitness = tetragram.fitness(encryptedText,
			newAlphabet);
		if (newFitness > fitness) {
		    fitness = newFitness;
		    bestAlphabet = newAlphabet;
		}
	    }

	    final String answer = Encryption.transcribeCipherText(
		    encryptedText, bestAlphabet);
	    int count = 0;
	    for (final Entry<Character, Character> entry : Utils.invert(
		    encryptionAlphabet).entrySet()) {
		if (entry.getValue().equals(bestAlphabet.get(entry.getKey()))
			&& encryptedText.contains(entry.getKey().toString())) {
		    count++;
		}
	    }
	    LOG.info("ANSWER: " + count + " correct\t" + answer);

	    if (answer.equals(plainText)) {
		correct++;
	    }
	}

	LOG.info("Correct decryptions: " + correct);
    }

    /**
     * Does multiple test runs using simmulated annealing and verifying its
     * results. It generates a random substitution from the source text.
     * 
     * @param text
     *            Source text
     */
    public void doSimmulatedAnnealingTestRun(final String text) {
	final Random r = new Random();
	final int textLength = 100;
	int correct = 0;

	for (int i = 0; i < 100; i++) {
	    final int start = r.nextInt(text.length() - textLength);

	    final String plainText = text.substring(start, start + textLength);
	    LOG.info("Plain text:\t\t" + plainText);

	    // encode
	    final Map<Character, Character> encryptionAlphabet = Encryption
		    .randomAlphabet();
	    final String encryptedText = Encryption.transcribeCipherText(
		    plainText, encryptionAlphabet);

	    Map<Character, Character> bestAlphabet = simmulatedAnnealing(
		    encryptedText, Encryption.randomAlphabet(), 0.01, 0.99, 800);

	    final String answer = Encryption.transcribeCipherText(
		    encryptedText, bestAlphabet);
	    int count = 0;
	    for (final Entry<Character, Character> entry : Utils.invert(
		    encryptionAlphabet).entrySet()) {
		if (entry.getValue().equals(bestAlphabet.get(entry.getKey()))
			&& encryptedText.contains(entry.getKey().toString())) {
		    count++;
		}
	    }
	    LOG.info("ANSWER: " + count + " correct\t" + answer);

	    if (answer.equals(plainText)) {
		correct++;
	    }
	}

	LOG.info("Correct decryptions: " + correct);
    }

    public int getCorrectAnalysis() {
	return correctAnalysis;
    }

    public static float indexOfCoincidence(String text) {
	final TObjectIntHashMap<Character> freq = new TObjectIntHashMap<Character>();

	for (int i = 0; i < text.length(); i++) {
	    freq.adjustOrPutValue(text.charAt(i), 1, 1);
	}

	float ic = 0;

	for (int i = 0; i < Encryption.plainAlphabet.length(); i++) {
	    ic += freq.get(Encryption.plainAlphabet.charAt(i))
		    * (freq.get(Encryption.plainAlphabet.charAt(i)) - 1);
	}

	return ic * Encryption.plainAlphabet.length()
		/ (text.length() * (text.length() - 1));
    }
}
