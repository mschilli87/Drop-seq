/*
 * MIT License
 *
 * Copyright 2017 Broad Institute
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.util.Histogram;
import htsjdk.samtools.util.IOUtil;
import picard.nio.PicardHtsPath;

public class FilterBamByTagTest {

	private static final int PAIRED_READS_ACCEPTED = 12;
	private static final int PAIRED_READS_REJECTED = 8;
	private static final int UNPAIRED_READS_ACCEPTED = 6;
	private static final int UNPAIRED_READS_REJECTED = 4;
	static final File PAIRED_INPUT_FILE=new File ("testdata/org/broadinstitute/dropseq/utils/paired_reads_tagged.bam");
	private static final File UNPAIRED_INPUT_FILE=new File ("testdata/org/broadinstitute/dropseq/utils/unpaired_reads_tagged.bam");
	private static final File PAIRED_INPUT_FILE_FILTERED=new File ("testdata/org/broadinstitute/dropseq/utils/paired_reads_tagged_filtered.bam");
	private static final File UNPAIRED_INPUT_FILE_FILTERED=new File ("testdata/org/broadinstitute/dropseq/utils/unpaired_reads_tagged_filtered.bam");
	private static final File PAIRED_INPUT_CELL_BARCODES=new File ("testdata/org/broadinstitute/dropseq/utils/paired_reads_tagged.cell_barcodes.txt");

	private static final File UNPAIRED_INPUT_FILE_FILTERED_AAAGTAGAGTGG=new File ("testdata/org/broadinstitute/dropseq/utils/unpaired_reads_tagged_filtered_AAAGTAGAGTGG.bam");
	
	private static final File UNPAIRED_INPUT_FILE_HISTOGRAMS = new File ("testdata/org/broadinstitute/dropseq/utils/unpaired_reads_tagged.histograms.txt");
	private static final File PAIRED_INPUT_FILE_HISTOGRAMS = new File ("testdata/org/broadinstitute/dropseq/utils/paired_reads_tagged.histograms.txt");
	
	/**
	 * Runs the CLP, in the given mode, and asserts success
	 * @param successThreshold If non-null, passed to PASSING_READ_THRESHOLD CLP argument
	 * @return the CLP object after doWork() called.
	 */
	private FilterBamByTag runClp(final boolean pairedMode, final Double successThreshold, final File tagsCountFile) throws IOException {
		FilterBamByTag f = new FilterBamByTag();
		
		f.TAG_COUNTS_FILE=tagsCountFile;		
		final String prefix;
		if (pairedMode) {
			f.INPUT=Collections.singletonList(new PicardHtsPath(PAIRED_INPUT_FILE));
			f.TAG_VALUES_FILE=PAIRED_INPUT_CELL_BARCODES;
			
			prefix = "paired_input";
		} else {
			f.INPUT=Collections.singletonList(new PicardHtsPath(UNPAIRED_INPUT_FILE));
			// For some reason, use the same file as for paired
			f.TAG_VALUES_FILE = PAIRED_INPUT_CELL_BARCODES;
			prefix = "unpaired_input";
		}
		f.TAG="XC";
		f.PAIRED_MODE = pairedMode;
		f.PASSING_READ_THRESHOLD = successThreshold;
		f.OUTPUT=File.createTempFile(prefix, ".bam");
		f.OUTPUT.deleteOnExit();
		f.SUMMARY=File.createTempFile(prefix, ".summary.txt");
		f.SUMMARY.deleteOnExit();
		Assert.assertEquals(f.doWork(), 0);
		return f;
	}

	@Test
	public void testDoWorkPaired () throws IOException {
		final FilterBamByTag f = runClp(true, null, null);
		//samtools view -c paired_reads_tagged_filtered.bam
		// 12
		// samtools view -c paired_reads_tagged.bam
		// 20

		List<FilteredReadsMetric> metrics = MetricsFile.readBeans(f.SUMMARY);
		Assert.assertEquals(PAIRED_READS_ACCEPTED, metrics.get(0).READS_ACCEPTED);
		Assert.assertEquals(PAIRED_READS_REJECTED, metrics.get(0).READS_REJECTED);
								
		// test with tag values file.
		List<String> tags = Collections.singletonList("XC");
		compareBAMTagValues(PAIRED_INPUT_FILE_FILTERED, f.OUTPUT, tags, 0);

	}

	@Test
	public void testDoWorkUnPaired () throws IOException {
		FilterBamByTag f = runClp(false, null, null);

		// samtools view -c unpaired_reads_tagged_filtered.bam
		// 6
		// samtools view -c unpaired_reads_tagged.bam 
		// 10
				
		List<FilteredReadsMetric> metrics = MetricsFile.readBeans(f.SUMMARY);
		Assert.assertEquals(UNPAIRED_READS_ACCEPTED, metrics.get(0).READS_ACCEPTED);
		Assert.assertEquals(UNPAIRED_READS_REJECTED, metrics.get(0).READS_REJECTED);

		// test with tag values file.
		List<String> tags = Collections.singletonList("XC");
		compareBAMTagValues(UNPAIRED_INPUT_FILE_FILTERED, f.OUTPUT, tags, 0);


		// test alternate path without tag values file.
		f.INPUT=Collections.singletonList(new PicardHtsPath(UNPAIRED_INPUT_FILE));
		f.OUTPUT=File.createTempFile("unpaired_input_single_cell", ".bam");
		f.TAG="XC";
		f.TAG_VALUE=Arrays.asList("AAAGTAGAGTGG");
		f.TAG_VALUES_FILE=null;
		f.PAIRED_MODE=false;
		f.OUTPUT.deleteOnExit();
		Assert.assertEquals(f.doWork(), 0);

		// test with tag values file.
		tags = Collections.singletonList("XC");
		compareBAMTagValues(UNPAIRED_INPUT_FILE_FILTERED_AAAGTAGAGTGG, f.OUTPUT, tags, 0);

	}
	
	@Test
	// additionally tests the optional tag value counts
	public void testDoWorkUnPairedWithTagValueCounts () throws IOException {
		
		File tagValueCounts=File.createTempFile("tagValueCounts", ".txt");
		tagValueCounts.deleteOnExit();
		
		FilterBamByTag f = runClp(false, null, tagValueCounts);

		// samtools view -c unpaired_reads_tagged_filtered.bam
		// 6
		// samtools view -c unpaired_reads_tagged.bam 
		// 10
				
		List<FilteredReadsMetric> metrics = MetricsFile.readBeans(f.SUMMARY);
		Assert.assertEquals(UNPAIRED_READS_ACCEPTED, metrics.get(0).READS_ACCEPTED);
		Assert.assertEquals(UNPAIRED_READS_REJECTED, metrics.get(0).READS_REJECTED);
		
		MetricsFile<FilteredReadsMetric, String> result = new MetricsFile<FilteredReadsMetric, String>();
		result.read(IOUtil.openFileForBufferedReading(tagValueCounts));
		List<Histogram<String>> hist= result.getAllHistograms();
		
		Histogram<String> accept= hist.get(0);
		Assert.assertEquals(accept.getValueLabel(), "READS_ACCEPTED");
		Assert.assertEquals((int) accept.getSumOfValues(), metrics.get(0).READS_ACCEPTED);
		
		Histogram<String> reject= hist.get(1);
		Assert.assertEquals(reject.getValueLabel(), "READS_REJECTED");
		Assert.assertEquals((int) reject.getSumOfValues(), metrics.get(0).READS_REJECTED);
		
		
		MetricsFile<FilteredReadsMetric, String> expectedResult = new MetricsFile<FilteredReadsMetric, String>();
		expectedResult.read(IOUtil.openFileForBufferedReading(UNPAIRED_INPUT_FILE_HISTOGRAMS));
		Assert.assertTrue(expectedResult.areHistogramsEqual(result));
		

	}
	
	@Test
	public void testDoWorkPairedithTagValueCounts () throws IOException {
		File tagValueCounts=File.createTempFile("tagValueCounts", ".txt");
		tagValueCounts.deleteOnExit();
		
		
		final FilterBamByTag f = runClp(true, null, tagValueCounts);
		//samtools view -c paired_reads_tagged_filtered.bam
		// 12
		// samtools view -c paired_reads_tagged.bam
		// 20

		List<FilteredReadsMetric> metrics = MetricsFile.readBeans(f.SUMMARY);
		Assert.assertEquals(PAIRED_READS_ACCEPTED, metrics.get(0).READS_ACCEPTED);
		Assert.assertEquals(PAIRED_READS_REJECTED, metrics.get(0).READS_REJECTED);
								
		MetricsFile<FilteredReadsMetric, String> result = new MetricsFile<FilteredReadsMetric, String>();
		result.read(IOUtil.openFileForBufferedReading(tagValueCounts));
		List<Histogram<String>> hist= result.getAllHistograms();
		
		// only 1 read of the pair is tagged!
		Histogram<String> accept= hist.get(0);
		Assert.assertEquals(accept.getValueLabel(), "READS_ACCEPTED");
		Assert.assertEquals((int) accept.getSumOfValues(), (int) (metrics.get(0).READS_ACCEPTED/2));
		
		Histogram<String> reject= hist.get(1);
		Assert.assertEquals(reject.getValueLabel(), "READS_REJECTED");
		Assert.assertEquals((int) reject.getSumOfValues(), (int) (metrics.get(0).READS_REJECTED/2));
		
		// because only 1 read of the pair is tagged, the results are the same as the unpaired test.
		MetricsFile<FilteredReadsMetric, String> expectedResult = new MetricsFile<FilteredReadsMetric, String>();
		expectedResult.read(IOUtil.openFileForBufferedReading(UNPAIRED_INPUT_FILE_HISTOGRAMS));
		Assert.assertTrue(expectedResult.areHistogramsEqual(result));
	
		
	}
	
	
	private double makePassingReadThreshold(final boolean pairedMode, final boolean fractionalThreshold, final boolean passing) {
		final int addend = passing? -1: 1;
		final double successThreshold;
		if (pairedMode) {
			if (fractionalThreshold) {
				successThreshold = (PAIRED_READS_ACCEPTED + addend)/(double)(PAIRED_READS_ACCEPTED + PAIRED_READS_REJECTED);
			} else {
				successThreshold = PAIRED_READS_ACCEPTED + addend;
			}
		} else if (fractionalThreshold) {
			successThreshold = (UNPAIRED_READS_ACCEPTED + addend)/(double)(UNPAIRED_READS_ACCEPTED + UNPAIRED_READS_REJECTED);
		} else {
			successThreshold = UNPAIRED_READS_ACCEPTED + addend;
		}
		return successThreshold;
	}

	@Test(dataProvider = "thresholdTestDataProvider")
	public void testPassingThreshold(final boolean pairedMode, final boolean fractionalThreshold) throws IOException {
		runClp(pairedMode, makePassingReadThreshold(pairedMode, fractionalThreshold, true), null);
	}

	@Test(dataProvider = "thresholdTestDataProvider", expectedExceptions = {RuntimeException.class})
	public void testFailingThreshold(final boolean pairedMode, final boolean fractionalThreshold) throws IOException {
		runClp(pairedMode, makePassingReadThreshold(pairedMode, fractionalThreshold, false), null);
	}

	@DataProvider(name="thresholdTestDataProvider")
	public Object[][] thresholdTestDataProvider() {
		return new Object[][] {
				{true, true},
				{true, false},
				{false, false},
				{false, true},
		};
	}

	@Test
	public void filterReadTest() {
		SAMRecord readHasAttribute = new SAMRecord(null);
		String tag = "XT";
		readHasAttribute.setAttribute(tag, "1");

		Set<String> values = new HashSet<>();
		values.add("1");

		SAMRecord readNoAttribute = new SAMRecord(null);

		FilterBamByTag t = new FilterBamByTag();
		// read has attribute, accept any value, want to retain read.
		boolean flag1 = t.filterRead(readHasAttribute, tag, null, true, null, false);
		Assert.assertFalse(flag1);

		// read has attribute, accept any value, want to filter read.
		boolean flag2 = t.filterRead(readHasAttribute, tag, null, false, null, false);
		Assert.assertTrue(flag2);

		// read has attribute, accept certain value, want to retain read.
		boolean flag3 = t.filterRead(readHasAttribute, tag, values, true, null, false);
		Assert.assertFalse(flag3);

		// read has attribute, accept certain value, want to filter read.
		boolean flag4 = t.filterRead(readHasAttribute, tag, values, false, null, false);
		Assert.assertTrue(flag4);

		// read does not have attribute, accept any value, want to retain read.
		boolean flag5 = t.filterRead(readNoAttribute, tag, null, true, null, false);
		Assert.assertTrue(flag5);

		// read does not have attribute, accept any value, want to filter read.
		boolean flag6 = t.filterRead(readNoAttribute, tag, null, false, null, false);
		Assert.assertFalse(flag6);

		// read does not have attribute, accept certain value, want to retain read.
		boolean flag7 = t.filterRead(readNoAttribute, tag, values, true, null, false);
		Assert.assertTrue(flag7);

		// read does not have attribute, accept certain value, want to filter read.
		boolean flag8 = t.filterRead(readNoAttribute, tag, values, false, null, false);
		Assert.assertFalse(flag8);
		
		// test map quality filtering
		
		readHasAttribute.setMappingQuality(10);
		boolean flag9 = t.filterRead(readHasAttribute, tag, null, true, 10, false);
		Assert.assertFalse(flag9);
		boolean flag10 = t.filterRead(readHasAttribute, tag, null, true, 20, false);
		Assert.assertTrue(flag10);
		
		// want to test partial matching.
		SAMRecord readHasGene = new SAMRecord(null);
		String geneNameTag = "gn";
		readHasGene.setAttribute(geneNameTag, "A,B");

		Set<String> geneValues = new HashSet<> (Arrays.asList("A"));
		boolean flagExact =t.filterRead(readHasGene, geneNameTag, geneValues, true, 0, false);
		boolean flagPartial =t.filterRead(readHasGene, geneNameTag, geneValues, true, 0, true);
		// filter the exact match
		Assert.assertTrue(flagExact);
		// don't filter the partial match
		Assert.assertFalse(flagPartial);
		
	}

	/**
	 * @return a paired read, first of pair in the first position of the list, 2nd of pair in the 2nd position.
	 */
	private List<SAMRecord> getPairedRead () {
		List<SAMRecord> result = new ArrayList<> ();

		SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
		builder.addUnmappedPair("test");
		Collection<SAMRecord> recs = builder.getRecords();

		for (SAMRecord r: recs) {
			if (r.getFirstOfPairFlag()) result.add(0, r);
			if (r.getSecondOfPairFlag()) result.add(1, r);
		}
		return (result);

	}

	@Test
	public void filterByReadNumberTest() {
		FilterBamByTag t = new FilterBamByTag();

		// record paired and read is 1st
		List<SAMRecord> recs = getPairedRead ();
		SAMRecord recFirstPaired = recs.get(0);
		SAMRecord recSecondPaired = recs.get(1);

		boolean flag1= t.retainByReadNumber(recFirstPaired, 1);
		boolean flag2= t.retainByReadNumber(recFirstPaired, 2);
		Assert.assertTrue(flag1);
		Assert.assertFalse(flag2);

		// record paired and read is 2st
		recSecondPaired.setProperPairFlag(true);
		recSecondPaired.setSecondOfPairFlag(true);
		flag1= t.retainByReadNumber(recSecondPaired, 1);
		flag2= t.retainByReadNumber(recSecondPaired, 2);
		Assert.assertTrue(flag2);
		Assert.assertFalse(flag1);

		// record unpaired and read is 1st
		SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
		builder.addUnmappedFragment("foo");
		SAMRecord recFirstUnPaired = builder.getRecords().iterator().next();

		flag1= t.retainByReadNumber(recFirstUnPaired, 1);
		flag2= t.retainByReadNumber(recFirstPaired, 2);
		Assert.assertTrue(flag1);
		Assert.assertFalse(flag2);
	}

	@Test
	public void testArgErrors () throws IOException {
		FilterBamByTag f = new FilterBamByTag();
		f.INPUT=Collections.singletonList(new PicardHtsPath(PAIRED_INPUT_FILE));
		f.OUTPUT=File.createTempFile("paired_input", ".bam");
		f.PAIRED_MODE=true;
		f.OUTPUT.deleteOnExit();
		Assert.assertSame(1, f.doWork());

	}

	private void compareBAMTagValues(File input1, File input2, List<String> tags, int expectedProgramValue) {
		CompareBAMTagValues cbtv = new CompareBAMTagValues();
		cbtv.INPUT_1 = Collections.singletonList(new PicardHtsPath(input1));
		cbtv.INPUT_2 = Collections.singletonList(new PicardHtsPath(input2));
		cbtv.TAGS_1 = tags;
		cbtv.TAGS_2 = tags;
		cbtv.STRICT = true;
		int result = cbtv.doWork();
		Assert.assertTrue(result == expectedProgramValue);
	}

}
