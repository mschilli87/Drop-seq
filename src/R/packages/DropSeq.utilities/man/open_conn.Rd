% Generated by roxygen2: do not edit by hand
% Please edit documentation in R/file_util.R
\name{open_conn}
\alias{open_conn}
\title{Creates a file connection with the given open mode.}
\usage{
open_conn(file, open = "")
}
\arguments{
\item{file}{If ends with ".gz", a gzfile() is created; else a regular file() connection.}

\item{open}{mode in which file is opened.  Default: "rb"}
}
\description{
Creates a file connection with the given open mode.
}
