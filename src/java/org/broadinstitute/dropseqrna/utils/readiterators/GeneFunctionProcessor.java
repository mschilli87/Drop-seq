package org.broadinstitute.dropseqrna.utils.readiterators;

import htsjdk.samtools.SAMRecord;
import org.broadinstitute.dropseqrna.annotation.functionaldata.*;
import org.broadinstitute.dropseqrna.barnyard.Utils;
import picard.annotation.LocusFunction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class abstracts out the logic needed by GeneFunctionIteratorWrapper, but removes the iterator
 * so that the logic can be reused on reads that are generated in different access patterns
 * @author nemesh
 *
 */
public class GeneFunctionProcessor {

	// the delimiter for BAM TAGs
	public static final String DELIMITER = ",";

	private final String geneTag;
	private final String strandTag;
	private final String functionTag;
	private final boolean assignReadsToAllGenes;


	private final FunctionalDataProcessorI fdp;
	
	public GeneFunctionProcessor(final String geneTag, final String strandTag, final String functionTag,
			final boolean assignReadsToAllGenes, final StrandStrategy strandFilterStrategy,
			final Collection<LocusFunction> acceptedLociFunctions, FunctionalDataProcessorStrategy functionStrategy) {
		

		this.geneTag = geneTag;
		this.strandTag = strandTag;
		this.functionTag = functionTag;
		this.assignReadsToAllGenes = assignReadsToAllGenes;
		this.fdp = FunctionalDataProcessorFactory.getFunctionalDataProcessor(strandFilterStrategy,
				acceptedLociFunctions, functionStrategy);
	}
	
	/**
	 * Process the read to test that it passes filters, then the functional annotations are limited to a single tuple
	 * of gene/strand/function for downstream interpretation.
	 * 
	 * @param r The read to process
	 * @return The output read(s).  
	 */
	public List<SAMRecord> processRead (final SAMRecord r) {
		List<SAMRecord> result = new ArrayList<>();

		List<FunctionalData> fdList = getReadFunctions(r, true);
		
		// If there's no functional data that passes the filters, return.
		if (fdList.isEmpty()) return result;

		// filter to only the preferred class to resolve reads that overlap a coding region on one gene
		// and an intronic region on a different gene.
		// this filters the data to just the coding gene.
		fdList = fdp.filterToPreferredAnnotations(fdList);

		// If there's only one functional data result, re-tag the read, and
		// queue it, then short circuit out.
		if (fdList.size() == 1) {
			FunctionalData fd = fdList.getFirst();
			SAMRecord rr = assignTagsToRead(r, fd);
			result.add(rr);
			return result;
		}
		// more than 1 read.
		if (this.assignReadsToAllGenes)
			// if fdList is empty, no records are added
			for (FunctionalData fd : fdList) {
				SAMRecord rr = assignTagsToRead(Utils.getClone(r), fd);
				result.add(rr);
			}
		return result;
	}

	/**
	 * For a list of reads that should be interpreted as a single "unit" (such as multiple reads that share the same query name).
	 * This is useful to interpret primary/secondary alignments of the same read.
	 * Filters reads to a preferred functional annotation (coding>intronic>intergenic), or none if ambiguous
	 * @param recs A list of SAMRecords that come from the same observation
	 * @return A read with tags that best represents this group.  This can also return null if read group is ambiguous.
	 */
	public SAMRecord processReads (final List<SAMRecord> recs) {
		List<FunctionalData> fdList = recs.stream()
				.flatMap(r -> getReadFunctions(r, true).stream())
				.collect(Collectors.toList());

		// If there's no functional data that passes the filters, return.
		if (fdList.isEmpty()) return null;

		// filter to only the preferred class to resolve reads that overlap a coding region on one gene
		// and an intronic region on a different gene.
		// this filters the data to just the coding gene.
		fdList = fdp.filterToPreferredAnnotations(fdList);

		// Why a set?  If there are multiple reads that have a single consistent result, we can use that.
		// This deals with multiple reads that map to the same gene, especially useful for MetaGene discovery where
		// the newly applied tag can be identical.
		// Some metagenes are on opposite strands, which makes this more complex.
		// equals might be better implemented as the interpreted functional annotation + gene name
		// instead of testing for the number of unique FunctionalData objects by equals, test the how
		// many have the same gene and type.

//		Set<FunctionalData> fdSet = new HashSet<>(fdList);
//
//		if (fdSet.size() == 1) {
//			FunctionalData fd = fdList.getFirst();
//            return (assignTagsToRead(recs.getFirst(), fd));
//		}

		// test if all functional annotations are the same as the first entry.
		FunctionalData first = fdList.getFirst();
		boolean allSame = fdList.stream().allMatch(first::sameGeneAndType);
		// if so, assign and finish.
		if (allSame)
            return (assignTagsToRead(recs.getFirst(), first));

		return null;
	}

	/**
	 * For a SAMRecord, generate the list of functional data captured by this read.
	 * This extracts the functional data tags from the read and converts it into the new domain.
	 * If filter is true, defer to the functional data processor strategy for how to further process the functional data.
	 * If filter is false, construct all functional data and pass it on.
	 * @param r The read to process
	 * @param filter Set to true to filter the output using the given FunctionalDataProcessorStrategy.
	 * @return A list of 0 or more FunctionalData objects.
	 */
	public List<FunctionalData> getReadFunctions(final SAMRecord r, boolean filter) {
		String geneList = r.getStringAttribute(this.geneTag);
		String strandList = r.getStringAttribute(this.strandTag);
		String functionList = r.getStringAttribute(this.functionTag);

		// If you're missing the gene, you can't use this read.
		// If care about strand, and you're missing the strand, you can't use this read.
		// If care about function, and you're missing the  function, you can't use this read.
		if ((geneList == null) ||
				(fdp.getStrandStrategy() != null && strandList == null) ||
				(!fdp.getAcceptedFunctions().isEmpty() && functionList == null)){
			return Collections.emptyList();
		}

		// there's at least one good copy of the read. Does the read match on
		// strand/gene, or is it assigned to multiple genes?
		final String[] genes = geneList.split(DELIMITER);
		final String[] strands = (strandList == null? null: strandList.split(DELIMITER));
		final LocusFunction[] locusFunctions = (functionList == null? null: getLocusFunctionFromRead(functionList));
		// if filtering is required, then apply filtering here.
		// otherwise build all functional data.
		List<FunctionalData> fdList;
		if (filter)
			fdList = fdp.getFilteredFunctionalData(genes, strands, locusFunctions, r.getReadNegativeStrandFlag());
		else
			fdList = FunctionalData.buildFD(genes, strands, locusFunctions, fdp.getStrandStrategy(), fdp.getAcceptedFunctions(), r.getReadNegativeStrandFlag());
		return (fdList);
	}





	private SAMRecord assignTagsToRead(final SAMRecord r,
			final FunctionalData fd) {
		r.setAttribute(geneTag, fd.getGene());
		r.setAttribute(strandTag, fd.getGeneStrand());
		if (fd.getLocusFunction() != null) {
			r.setAttribute(functionTag, fd.getLocusFunction().name());
		}
		return (r);
	}

	public static LocusFunction[] getLocusFunctionFromRead(final String functionList) {
		String[] fl = functionList.split(DELIMITER);
		LocusFunction[] result = new LocusFunction[fl.length];
		for (int i = 0; i < fl.length; i++) {
			LocusFunction lf = LocusFunction.valueOf(fl[i]);
			result[i] = lf;
		}
		return result;
	}

	public FunctionalDataProcessorI getFdp() {
		return fdp;
	}

}
