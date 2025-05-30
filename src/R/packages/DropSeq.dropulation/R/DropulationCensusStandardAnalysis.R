
#This is for R CMD CHECK
if(getRversion() >= "2.15.1")  utils::globalVariables(c("."))


#' Run standard analysis for census, roll call, and csi.
#' 
#' Run standard analysis for census, roll call, and csi.  
#' All input files are optional, plots will be generated based on the files that are not null.
#' 
#' While many inputs are optional, CensusSeq should be run in combination with RollCall and CSI to
#' fully evaluate a donor pool. 
#' @param readQualityMetricsFile A file containing the number of reads that aligned in the experiment. 
#' This file is generated by Picard's CollectMultipleMetrics. (optional)
#' @param censusFile The dropulation census output file
#' @param rollCallFile The Roll Call output file
#' @param csiFile The CSI output file
#' @param donorListFile A list of donors expected in the data set.
#' @param expName The name of the experiment.
#' @param outPDF A file location where plots will be generated. (optional)
#' @param minNumSNPS The minimum number of SNPs to consider a roll call result to be well powered.  
#' @export
#' @import grDevices graphics utils stats data.table
censusSeqQC<-function (readQualityMetricsFile=NULL, censusFile=NULL, rollCallFile=NULL, csiFile=NULL, donorListFile=NULL, expName="", outPDF=NULL, minNumSNPS=1000) {
    if (!is.null(outPDF)) pdf(outPDF)
    if (!is.null(readQualityMetricsFile)) plotReadQualityMetrics(readQualityMetricsFile, expName)
	if (!is.null(censusFile)) csiNumSNPsPlot(csiFile, expName)
	if (!is.null(rollCallFile) & !is.null(donorListFile)) plotRollCallNumSNPs(rollCallFile, donorListFile, minNumSNPS, expName)
	if (!is.null(rollCallFile) & !is.null(censusFile)) plotRollCall(rollCallFile, censusFile, donorListFile, expName)
	if (!is.null(rollCallFile) & !is.null(censusFile)) plotCensusRollCall(rollCallFile, censusFile, donorListFile, expName)
	if (!is.null(outPDF)) dev.off()
}


#Adding this plot back in.
plotCensusRollCall<-function (rollCallFile, censusFile, donorListFile, expName) {
	#TO make R CMD CHECK happy.  Blame ggplot2.
	REP_IRVs=CENSUS=NULL
	
	h=read.table(censusFile, comment.char = "@", nrows =1, fill=T, skip=1, sep="\t", stringsAsFactors = F)
	a=read.table(rollCallFile, header=T, stringsAsFactors=F, sep="\t")
	b=read.table(censusFile, header=T, stringsAsFactors = F, sep="\t")
	
	numCensus=dim (b)[1]
	
	#roll call and census can now be disjoint sets of donors when highly related donors are
	#removed from the roll call analysis, so need to match to the tested set.
	both=intersect (a$DONOR, b$DONOR)
	a=a[match(both, a$DONOR),]
	b=b[match(both, b$DONOR),]
	
	df=data.frame(DONOR=a$DONOR, REP_IRVs=a$REP_IRVs, NUM_SNPS=a$NUM_SNPS, CENSUS=b$REPRESENTATION, stringsAsFactors = F)
	
	allXY=c(df$REP_IRVs, df$CENSUS)
	allXY=allXY[allXY>0]
	xyLim=c(min(floor (log10(allXY))), max(ceiling (log10(allXY))))
	
	#for any value of 0, set to the minimum to avoid log10 issues.
	df$REP_IRVs[df$REP_IRVs==0]=min(allXY)
	
	#log10 data before plotting.
	df$REP_IRVs_log10=log10(df$REP_IRVs)
	df$CENSUS_log10=log10(df$CENSUS)
	
	p=ggplot(df, aes(x = log10(REP_IRVs), y = log10(CENSUS))) +
		geom_point() +
		scale_x_continuous(breaks = seq(xyLim[1], xyLim[2], by = 1), 
						   labels = function(x) as.character(10^x), limits=xyLim) +
		scale_y_continuous(breaks = seq(xyLim[1], xyLim[2], by = 1), 
						   labels = function(x) as.character(10^x), limits=xyLim) +
		labs(x = "Roll Call [log10]", y = "Census [log10]") +
		geom_abline(slope = 1, intercept = 0, color = "grey", linetype = "dashed") +
		geom_hline(yintercept = log10(0.003), color = "red", linetype = "dashed") +
		theme(axis.text.x = element_text(size = 10), 
			  axis.text.y = element_text(size = 10),
			  axis.title = element_text(size = 12),
			  plot.title = element_text(size = 14))
		
	
	#add census converged info.
	strTitle2=""
	strTitle3=""
	if (h$V3=="CONVERGED=false") strTitle2="Census did not converge"
	#if roll call didn't ascertain all individuals
	if (numCensus>length(both)) {
		strTitle3=paste("Samples Ascertained: Roll Call [", length(both),"] Census [",  numCensus, "]")
	}
	p=p+ggtitle (paste(expName, "\n", strTitle2, "\n", strTitle3))
	print (p)
}

plotRollCallNumSNPs<-function (rollCallFile, donorListFile, minNumSNPS=1000, expName) {
	#TO make R CMD CHECK happy.  Blame ggplot2.
	order_index=NUM_SNPS=categoryNames=NULL
	
	a=read.table(rollCallFile, header=T, stringsAsFactors = F, sep="\t")
	samples=read.table(donorListFile, header=F, stringsAsFactors = F, sep="\t")$V1
	a$IN_POOL=F
	a$SUFFICIENT_SNPS=F
	if (any(a$NUM_SNPS>=minNumSNPS)) a[a$NUM_SNPS>=minNumSNPS,]$SUFFICIENT_SNPS=T
	#donors can be missing from the sample list because they are highly related.
	#limit sample list to donors tested.
	samples=intersect(samples, a$DONOR)
	a[match(samples, a$DONOR),]$IN_POOL=T
	a=a[order (a$IN_POOL, a$NUM_SNPS),]
	getCol<-function (x) {
		if (x[["IN_POOL"]]==F && x[["SUFFICIENT_SNPS"]]==F) return ("orange")
		if (x[["IN_POOL"]]==F && x[["SUFFICIENT_SNPS"]]==T) return ("red")
		if (x[["IN_POOL"]]==T && x[["SUFFICIENT_SNPS"]]==F) return ("blue")
		if (x[["IN_POOL"]]==T && x[["SUFFICIENT_SNPS"]]==T) return ("green")
	}
	
	a$colors=as.vector(apply(a[c("IN_POOL", "SUFFICIENT_SNPS")], 1, getCol))
	
	# Reorder factor levels to maintain sorting within categories
	# Create an index for ordering
	# Sort the data frame by color and NUM_SNPS
	a <- a[order(a$colors, a$NUM_SNPS), ]
	
	# Create an index for ordering
	a$order_index <- 1:dim(a)[1]
	
	p=ggplot(a, aes(x = order_index, y = log10(NUM_SNPS), fill=colors )) +
		geom_bar(stat = "identity", aes(colour=colors), show.legend=T) +
		labs(x = "Donors", y = "Num SNPs", fill = "Category") +
		theme_minimal() +
		scale_color_identity() +
		scale_fill_manual(values = c("blue", "green", "orange", "red"),
						  labels = c("in pool insufficient SNPs", "in pool sufficient SNPs",
									"outside pool insufficient SNPs", "outside pool sufficient SNPs")) +
		ggtitle(paste(expName, "\nSNPs ascertained by Roll Call in Pool")) +
		theme(legend.position = "top") +
		guides(fill=guide_legend(nrow=2,byrow=TRUE))
	print (p)
	
	# Calculate counts
	counts <- c(
		sum(a$IN_POOL == TRUE & a$SUFFICIENT_SNPS == FALSE),
		sum(a$IN_POOL == TRUE & a$SUFFICIENT_SNPS == TRUE),
		sum(a$IN_POOL == FALSE & a$SUFFICIENT_SNPS == FALSE),
		sum(a$IN_POOL == FALSE & a$SUFFICIENT_SNPS == TRUE)
	)
	
	# Create a data frame for plotting
	plot_data <- data.frame(categoryNames = c("in pool insufficient SNPs", "in pool sufficient SNPs",
											  "outside pool insufficient SNPs", "outside pool sufficient SNPs"),
							counts = counts)
	
	p= ggplot(plot_data, aes(y = categoryNames, x = counts)) +
		geom_bar(stat = "identity", fill = "lightblue") +
		geom_text(aes(label = counts, x = max(counts) / 2), hjust=0.5, size = 7) +
		labs(x = "Counts", y = NULL) +
		ggtitle(paste(expName, "\nSNPs ascertained by Roll Call")) +
		theme_minimal() +
		theme(axis.text.y = element_text(angle = 0, hjust = 0.5, vjust = 0.5))
	print (p)
}

csiNumSNPsPlot<-function (csiFile, expName) {
	#TO make R CMD CHECK happy.  Blame ggplot2.
	values=NULL
	
    h=read.table(csiFile, comment.char = "@", nrows =1, fill=T, skip=1, sep="\t")
    a=read.table(csiFile, header=T, stringsAsFactors = F ,sep="\t")
    aa=c(a$NUM_SNPS, a$REF_COUNT, a$ALT_COUNT)
    
    df=data.frame(SNPs=a$NUM_SNPS, "Ref Alleles"=a$REF_COUNT, "Alt Alleles"=a$ALT_COUNT, check.names = F)
    
    categories = c("SNPs", "Ref Alleles", "Alt Alleles")
    plot_data <- data.frame(categories = categories, values = aa)
    plot_data$categories=factor(plot_data$categories, levels=categories)
    
    fracAltStr=paste("(", round (a[['FRAC_ALT']]*100,3), "%)", sep="")
    strTitle=paste(expName, "- CSI summary\n", "% Contamination [", round (a$PCT_CONTAMINATION,2), "]",  sep="")
    
    p=ggplot(plot_data, aes(x = categories, y = values)) +
    	geom_bar(stat = "identity", fill = "lightblue") +
    	geom_text(aes(label = ifelse(categories == "Alt Alleles", a$ALT_COUNT, ""), y= max(df)/2), 
    			  position = position_dodge(width = .9), vjust = 0.5, size=6) +
    	geom_text(aes(label = ifelse(categories == "Alt Alleles", fracAltStr, ""), y= max(df)/3), 
    			  position = position_dodge(width = .9), vjust = 0.5, size=6) +
    	theme(axis.text.x = element_text(size = 14), 
    		  axis.text.y = element_text(size = 10),
    		  axis.title = element_text(size = 12),
    		  plot.title = element_text(size = 16)) +
    	ggtitle(strTitle) +
    	labs(x = "", y = "")
    
    print (p)
    
}

plotReadQualityMetrics<-function (readQualityMetricsFile, expName) {
	#TO make R CMD CHECK happy.  Blame ggplot2.
	values=NULL
	
    a=read.table(readQualityMetricsFile, header=T, stringsAsFactors=F, sep="\t", nrows=3)
    if (dim(a)[1]>1) {
        a=a[a$CATEGORY=="PAIR",]
    }
    aa=a[,c("PF_READS", "PF_READS_ALIGNED", "PF_HQ_ALIGNED_READS")]
    aa=round (as.numeric(aa)/1e6,1)
    z=barplot (aa, col="light blue", names.arg=c("total", "aligned", "HQ aligned"), ylab="Reads [millions]", cex.axis=1.5, cex.lab=1.5, cex.names = 1.5)
    pct=paste (round (aa/max(aa)*100,1), "%")
    text (z[,1], max(aa)/2, labels=pct, cex=2)
    title(expName, cex.main=1.5)
    
    categories = c("total", "aligned", "HQ aligned")
    plot_data <- data.frame(categories = categories, values = aa, pct=pct)
    plot_data$categories=factor(plot_data$categories, levels=categories)
    
    ggplot(plot_data, aes(x = categories, y = values)) +
    	geom_bar(stat = "identity", fill = "lightblue") +
    	geom_text(aes(label = pct), vjust = 0, y=max(plot_data$values)/2, size=8) +
    	theme(axis.text.x = element_text(size = 14), 
    		  axis.text.y = element_text(size = 10),
    		  axis.title = element_text(size = 14),
    		  plot.title = element_text(size = 16)) +
    	ggtitle(expName) +
    	labs(x = "", y = "Reads [millions]")
    
}


#############################################################################
# Dmeyer Roll Call plot code.
# This could would be nice to simplify somewhat.
#############################################################################

plotRollCall<-function (rollCallFile, censusFile, donorListFile, expName) {
	h=read.table(censusFile, comment.char = "@", nrows =1, fill=T, skip=1, sep="\t", stringsAsFactors = F)
	
	#add census converged info.
	strTitle2=""
	if (h$V3=="CONVERGED=false") {
		title (paste(expName, "\n", strTitle2))
	} else {
		preppedRollcallData <- prepareRollcallData(rollCallFile,donorListFile,expName)
		print(plotRollcalls(preppedRollcallData,logScale=TRUE, flipAxes=TRUE,colors= c("black","#4594f5")))
	}
}


#' Plotting of rollcall data
#'
#' @param preppedRollcallData dataframe containg rollcall data generated using
#' \code{\link{prepareRollcallData}}
#' @param labelOutliers logical flag set to label any "intruders" or "no-shows"
#' @param logScale logical flag set to log-transform data
#' @param jitterHeight How much to jitter the plot vertically
#' @param pointsAlpha Transparency of points
#' @param labelAlpha Transparency of labels
#' @param labelSize Font size of labels
#' @param flipAxes logical flag to orient x/y axes. Defaults to FALSE, with REP_IRVs on the x-axis
#' @param colors colors for different classes (Expected, [Possible], Unexpected)
#'
#' @return ggplot object of plot for given rollcall data
#'
#' @import ggplot2
#' @importFrom ggrepel geom_label_repel
#' @importFrom data.table rbindlist
#' @importFrom purrr compose
#' @noRd
plotRollcalls <- function(preppedRollcallData, labelOutliers=F, logScale=F, 
                          jitterHeight=0.2, 
                          pointsAlpha=0.5,
                          labelAlpha=0.7,
                          labelSize=5,
                          flipAxes=FALSE,
                          colors=c("black", "#d18017", "red")) {

	
	#TO make R CMD CHECK happy.  Blame ggplot2.
	ID=REP_IRVs=NUM_SNPS=DONOR=Expected=isOutlier=NULL
	
	if (is.list(preppedRollcallData))
    	preppedRollcallData <- rbindlist(preppedRollcallData)

	#avoid warnings when REP_IRV=0 and Y axis on log scale by adding a small number.
	if (logScale) {
		smallNumber=min (preppedRollcallData[preppedRollcallData$REP_IRVs>0,]$REP_IRVs)/2
  		preppedRollcallData$REP_IRVs=preppedRollcallData$REP_IRVs+smallNumber
	}
  	
  
	# Check how many levels preppedData$Expected has
	nLevels <- length(unique(levels(preppedRollcallData$Expected)))
	if (nLevels == 2 && length(colors) == 3) colors <- colors[-2]

	jitterpos <- ggplot2::position_jitter(width=0,height = jitterHeight, seed=2)
  
	# Set ID as factor. Reverse if ID is on the y-axis (default)
	preppedRollcallData$ID <- (function(x) { 
       dirFun=ifelse(flipAxes, identity, rev)
       lvls=purrr::compose(dirFun, unique,as.character)(x)
       return (factor(x, lvls))
    })(preppedRollcallData$ID)
           
	p <- ggplot2::ggplot(preppedRollcallData, 
        	aes(y=ID, x=REP_IRVs, size=NUM_SNPS, label=DONOR, color=Expected)) +
    		geom_jitter(alpha=pointsAlpha, position = jitterpos)
	if (labelOutliers) {
    	toLabel <- preppedRollcallData[isOutlier,]
    	p <- 
    	p + geom_label_repel(data=toLabel, position=jitterpos, size=labelSize, 
                           alpha=labelAlpha, min.segment.length=0, force=T)
	}
	if (logScale)
    	p <- p + scale_x_continuous(trans='log10') + labs(x="REP_IRVs [log10]")

	p <- p+labs(color='', y='')+
    	scale_color_manual(values = colors)+
    	theme_linedraw()
	if (flipAxes)
    	p <- p + coord_flip()
	return (p)
}


### Data preparation functions ###

#' Preparation of rollcall data to be done before plotting
#'
#' @param rollcallFiles character vector of rollcall file paths
#' @param donorFiles character vector of line-separated donor files
#' containing a subset of donors from the VCF with which rollcall
#' was run.
#' @param ids labels given to eah rollcall dataset. Added as ID column.
#'
#' @return list of pepared rollcall dataframes
#' @noRd
prepareRollcallData <- function(rollcallFiles, donorFiles, ids=NULL) {
  validateRollcallFiles(rollcallFiles, donorFiles)

  ids <- validateAndExtractIds(rollcallFiles, ids)
  rollcallDfs <- loadRollcallFilesWithIds(rollcallFiles, ids)
  expectedDonors <- lapply(donorFiles, readLines)

  rollcallDfsWithExpectedDonors <- 
    mapply(annotateExpectedDonors, rollcallDfs, expectedDonors, SIMPLIFY=FALSE)

  result <- rollcallDfsWithExpectedDonors 
  names(result) <- ids
  return (result)
}



### Validation functions ###

#' Loading of rollcall file and addition of column specifying an ID for it
#'
#' @param rollcallFiles character vector of rollcall files
#' @param ids character vector of ids to assign to data from each rollcall file,
#' added as a new column ID to the parsed contents of each rollcall file
#' @importFrom data.table fread
#' @noRd
loadRollcallFilesWithIds <- function(rollcallFiles, ids) {
  loadSingleFileWithId <- function(file, id) {
    res <- fread(file)
    res$ID <- id
    return (res)
  }
  mapply(loadSingleFileWithId, rollcallFiles, ids, SIMPLIFY=FALSE)
}


#' Validation of ids variable if provided, extraction of ids if NULL
#'
#' @param rollcallFiles A list of roll call files to process 
#' @param ids The experiment or pool names for the roll call files.  Should be 
#' the same length as the rollcallFiles parameter and all entries must be unique
#' @importFrom stringr str_remove
#' @noRd
validateAndExtractIds <- function(rollcallFiles, ids) {
  if (is.null(ids))
    ids <- str_remove(basename(rollcallFiles), ".roll_call.txt$")
  else if (length(ids) != length(unique(ids)))
    stop("All ids must be unique")
  else if (length(ids) != length(rollcallFiles))
    stop("Must provide one id per rollcall file or set `ids` to NULL")
  return (ids)
}

#' Validation of the existence of files
#'
#' @param filepaths character vector of filepaths to validate
#' @noRd
validateFilepaths <- function(filepaths) {
  validFiles <- file.exists(filepaths)
  if (!all(validFiles)) {
    stop(paste("The following provided files do not exist:", 
               paste('"',filepaths,'"', collapse=",", sep="")))
  }
}

#' Validation of files needed to prepare a rollcall dataset
#'
#' @param rollcallFiles character vector of rollcall files to validate
#' @param donorFiles character vector of donor files to validate 
#' @noRd
validateRollcallFiles <- function(rollcallFiles, donorFiles) {
  if (length(rollcallFiles) != length(donorFiles)) {
    if (length(donorFiles) != 1) {
      stop(paste("`rollcallFiles` and `donorFiles` must be of same",
                 "length unless donorFiles is of length 1"))
    }
  }
  validateFilepaths(rollcallFiles)
  validateFilepaths(donorFiles)
}


### Annotation functions ###

#' Annotation of whether or not donors were expected to show up as present in
#' rollcall
#' 
#' @param rollcallDf dataframe containing rollcall results
#' @param expectedDonors character vector containing subset of donors from 
#' the VCF with which rollcall was run.
#' @return list of rollcall dataframes with added `Expected` column
#' to specify whether or not the sample was in the list of expected donors
#' @noRd
annotateExpectedDonors <- function(rollcallDf, expectedDonors) {
  rollcallDf$Expected <- 
    factor(ifelse(rollcallDf$DONOR %in% expectedDonors, "Expected", "Unexpected"),
           c("Expected", "Unexpected"))
  return (rollcallDf)
}

