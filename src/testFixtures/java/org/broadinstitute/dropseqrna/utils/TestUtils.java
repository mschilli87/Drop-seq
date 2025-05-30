/*
 * MIT License
 *
 * Copyright 2018 Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.broadinstitute.dropseqrna.utils;

import htsjdk.samtools.*;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.util.*;
import htsjdk.samtools.util.zip.DeflaterFactory;
import htsjdk.samtools.util.zip.InflaterFactory;
import org.broadinstitute.dropseqrna.barnyard.digitalexpression.tools.DGEMatrix;
import org.testng.Assert;
import picard.nio.PicardHtsPath;
import picard.util.TabbedInputParser;
import picard.util.TabbedTextFileWithHeaderParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

/**
 * Helper methods for
 * @author nemesh
 *
 */
public class TestUtils {

	private static final Log LOG = Log.getInstance(TestUtils.class);

	/**
	 * Test if two text files are the same, ignoring "#" character lines.
	 */
	public static boolean testFilesSame (final File expected, final File actual) {
		return testFilesSame(expected, actual, true);
	}

	public static boolean testFilesSame (final File expected, final File actual, boolean treatGroupedDelimitersAsOne) {
		TabbedInputParser e = new TabbedInputParser(treatGroupedDelimitersAsOne, expected);
		TabbedInputParser a = new TabbedInputParser(treatGroupedDelimitersAsOne, actual);
		while (e.hasNext() && a.hasNext()) {
			String le[] = e.next();
			String la[] = a.next();
			if (!Arrays.equals(le, la)) {
				CloserUtil.close(e);
				CloserUtil.close(a);
				return false;
			}
		}
		CloserUtil.close(e);
		CloserUtil.close(a);
		// one of the files is incomplete.
        return !e.hasNext() && !a.hasNext();
    }

	/**
	 * File comparison for files with values that may jiggle a little.
	 * Call Log.setGlobalLogLevel(LogLevel.DEBUG) to get additional info on differences
	 * @param expected tsv with header
	 * @param actual tsv with header
	 * @param transformers Map of function to transform strings into values that can be compared,
	 *                     if the whole lines don't agree.  Key: 0-based column index.  Value: transformer function.
	 * @return true if the files match
	 */
	public static boolean testTabularFilesSame (final File expected, final File actual,
												final Map<Integer, Function<String, Object>> transformers) {
		TabbedTextFileWithHeaderParser expectedParser = new TabbedTextFileWithHeaderParser(expected);
		TabbedTextFileWithHeaderParser actualParser = new TabbedTextFileWithHeaderParser(actual);
		if (!expectedParser.columnLabels().equals(actualParser.columnLabels())) {
			LOG.debug("Column labels in files %s and %s do not agree", expected, actual);
			return false;
		}
		CloseableIterator<TabbedTextFileWithHeaderParser.Row> e = expectedParser.iterator();
		CloseableIterator<TabbedTextFileWithHeaderParser.Row> a = actualParser.iterator();

		int lineNumber = 0;
		while (e.hasNext() && a.hasNext()) {
			++lineNumber;
			final TabbedTextFileWithHeaderParser.Row expectedRow = e.next();
			final TabbedTextFileWithHeaderParser.Row actualRow = a.next();
			String le = expectedRow.getCurrentLine();
			String la = actualRow.getCurrentLine();
			if (!le.equals(la)) {
				if (expectedRow.getFields().length != actualRow.getFields().length) {
					LOG.debug(String.format(
							"Number of fields differ at line %d for files %s and %s", lineNumber, expected, actual));
				} else {
					boolean failed = false;
					for (int i = 0; i < expectedRow.getFields().length; ++i) {
						final String expectedField = expectedRow.getFields()[i];
						final String actualField = actualRow.getFields()[i];
						final Object expectedTransformedField;
						final Object actualTransformedField;
						if (transformers.containsKey(i)) {
							final Function<String, Object> transformer = transformers.get(i);
							expectedTransformedField = transformer.apply(expectedField);
							actualTransformedField = transformer.apply(actualField);
						} else {
							expectedTransformedField = expectedField;
							actualTransformedField = actualField;
						}
						if (!expectedTransformedField.equals(actualTransformedField)) {
							LOG.debug(String.format(
									"In files %s and %s  at line %d, column %d, '%s' != '%s'; transformed: '%s' != '%s'",
									expected, actual, lineNumber, i+1, expectedField, actualField, expectedTransformedField, actualTransformedField));
							failed = true;
							break;

						}
					}
					if (!failed) continue;
				}
				CloserUtil.close(e);
				CloserUtil.close(a);
				return false;
			}
		}
		CloserUtil.close(e);
		CloserUtil.close(a);
		// one of the files is incomplete.
        return !e.hasNext() && !a.hasNext();
    }

	// Copied roughly from Picard CompareMetrics
	public static boolean testMetricsFilesEqual(final File expected, final File actual) throws FileNotFoundException {
		final MetricsFile<?, ?> metricsA = new MetricsFile<>();
		final MetricsFile<?, ?> metricsB = new MetricsFile<>();
		metricsA.read(new FileReader(expected));
		metricsB.read(new FileReader(actual));
		return metricsA.areMetricsEqual(metricsB) && metricsA.areHistogramsEqual(metricsB);
	}

	public static void assertSamFilesSame(final File actual, final File expected, boolean testHeader) {
		final SamReader expectedReader = SamReaderFactory.makeDefault().open(expected);
		final SamReader actualReader = SamReaderFactory.makeDefault().open(actual);
		try {
			SAMFileHeader eh= expectedReader.getFileHeader();
			SAMFileHeader ah= actualReader.getFileHeader();			
			if (testHeader) 
				Assert.assertEquals(eh, ah);
			assertSamRecordsSame(actual, expected, expectedReader, actualReader);
		} finally {
			CloserUtil.close(expectedReader);
			CloserUtil.close(actualReader);
		}
	}
	
	public static void assertSamFilesSame(final File actual, final File expected) {
		assertSamFilesSame(actual, expected, true);
	}
	
	public static void assertSamRecordsSame(File actual, File expected) {
		final SamReader expectedReader = SamReaderFactory.makeDefault().open(expected);
		final SamReader actualReader = SamReaderFactory.makeDefault().open(actual);
		try {
			assertSamRecordsSame(actual, expected, expectedReader, actualReader);
		} finally {
			CloserUtil.close(expectedReader);
			CloserUtil.close(actualReader);
		}
	}

	public static void assertSamRecordsSame(File actual, File expected, SamReader expectedReader, SamReader actualReader) {
		final SAMRecordIterator expectedIterator = expectedReader.iterator();
		final SAMRecordIterator actualIterator = actualReader.iterator();
		while (expectedIterator.hasNext()) {
			if (!actualIterator.hasNext()) {
				Assert.fail(String.format("%s has more records than %s", expected, actual));
			} else {
				Assert.assertEquals(actualIterator.next(), expectedIterator.next());
			}
		}
		if (actualIterator.hasNext()) {
			Assert.fail(String.format("%s has fewer records than %s", expected, actual));
		}
	}

	public static boolean isMacOs() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}

	/**
	 * CommandLineProgram only replaces default inflater/deflater with Intel versions, which don't work on Mac.
	 * Once they've been set to Intel versions in a JVM, they need to be reverted explicitly by any unit test.
	 * that is failing on Mac.
	 */
	public static void setInflaterDeflaterIfMacOs() {
		if (isMacOs()) {
			BlockCompressedOutputStream.setDefaultDeflaterFactory(new DeflaterFactory());
			BlockGunzipper.setDefaultInflaterFactory(new InflaterFactory());
		}
	}

	/**
	 * Get a unique temporary file via File.createTempFile, and mark it deleteOnExit
	 * @param prefix passed to File.createTempFile
	 * @param suffix passed to File.createTempFile
	 * @param periodlessExtensions Each of these is appended to the return value (with a period) and marked deleteOnExit
	 * @return the temporary file
	 */
	public static File getTempReportFile (final String prefix, final String suffix, final String... periodlessExtensions) {
        File tempFile;

        try {
            tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();
			for (final String extension : periodlessExtensions) {
				new File(tempFile.getParentFile(), tempFile.getName() + "." + extension).deleteOnExit();
			}
        } catch (IOException ex) {
            throw new RuntimeException("Error creating a temp file", ex);
        }
        return tempFile;
    }

	public static File createTempDirectory(final String prefix) {
		try {
			File ret = Files.createTempDirectory(prefix).toFile();
			ret.deleteOnExit();
			return ret;
		} catch (IOException e) {
			throw new RuntimeIOException(e);
		}
	}

	/**
	 * @return true if l is sorted (equal values allowed)
	 */
	public static <T extends Comparable> boolean testSorted(final List<T> l) {
		T prev = null;
		for (T v : l) {
			if (prev == null) {
				prev = v;
			} else if (prev.compareTo(v) > 0) {
				return false;
			}
		}
		return true;
	}

	public static void markBamsDeleteOnExit(final File fileWithSlug, final String slug, final int count) {
		final File dir = fileWithSlug.getParentFile();
		final String filename = fileWithSlug.getName();
		for (int i = 0; i < count; ++i) {
			new File(dir, filename.replace(slug, Integer.toString(i))).deleteOnExit();
		}
	}

    public static long countSamRecords(final File f) {
        SamReaderFactory factory = SamReaderFactory.makeDefault();
        SamReader reader = factory.open(f);
        long count=0;
        for (SAMRecord ignored : reader) {
            count++;
        }
        CloserUtil.close(reader);
        return count;
    }

    public static List<File> splitBamFile(File inputBAM, int numOutputs) {
        final SplitBamByCell bamSplitter = new SplitBamByCell();

        File outputBAM = getTempReportFile("SplitBamByCell.", bamSplitter.OUTPUT_SLUG + ".bam");
        File outputBAMList = getTempReportFile("SplitBamByCell.", ".list");
        bamSplitter.INPUT = Collections.singletonList(new PicardHtsPath(inputBAM));
        bamSplitter.OUTPUT = outputBAM;
        bamSplitter.OUTPUT_LIST = outputBAMList;
        bamSplitter.NUM_OUTPUTS = numOutputs;
        bamSplitter.DELETE_INPUTS = false;
        bamSplitter.USE_JDK_DEFLATER = isMacOs();
        bamSplitter.REPORT = getTempReportFile("SplitBamByCell.", SplitBamByCell.BAM_REPORT_EXTENSION);
        setInflaterDeflaterIfMacOs();
        Assert.assertEquals(bamSplitter.doWork(), 0);

        List<File> splitBAMFileList = new ArrayList<>();
        for (String filePath : IOUtil.readLines(outputBAMList)) {
            File bamFile = new File(filePath);
            splitBAMFileList.add(bamFile);
            bamFile.deleteOnExit();
        }

        return splitBAMFileList;
    }

	public static boolean dgeMatricesAreEqual(File matrixFile1, File matrixFile2) {
		return dgeMatricesAreEqual(DGEMatrix.parseFile(matrixFile1), DGEMatrix.parseFile(matrixFile2));
	}

	public static boolean dgeMatricesAreEqual(final DGEMatrix matrix1, DGEMatrix matrix2) {

		if (!matrix2.getGenes().equals(matrix1.getGenes()))
			return false;

		if (matrix2.getCellBarcodes().size() != matrix1.getCellBarcodes().size())
			return false;

		Map<String, Integer> indexMap = new HashMap<>();
		for (int idx=0; idx<matrix1.getCellBarcodes().size(); idx++) {
			indexMap.put(matrix1.getCellBarcodes().get(idx), idx);
		}

		int[] indexLookup = new int[matrix1.getCellBarcodes().size()];
		for (int idx2=0; idx2<matrix2.getCellBarcodes().size(); idx2++) {
			Integer idx1 = indexMap.get(matrix2.getCellBarcodes().get(idx2));
			if (idx1 == null)
				return false;

			indexLookup[idx2] = idx1;
		}

		for (String gene : matrix1.getGenes()) {
			double[] expression1 = matrix1.getExpression(gene);
			double[] expression2 = matrix2.getExpression(gene);
			for (int idx2=0; idx2<matrix2.getCellBarcodes().size(); idx2++) {
				if (expression2[idx2] != expression1[indexLookup[idx2]])
					return false;
			}
		}

		return true;
	}
}
