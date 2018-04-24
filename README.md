# BGPCommunitiesMon

CommunityMon can be used for the analysis of historical or real-time BGP data to
detect routing events based on changes on the values of the [BGP Commuinities](ftp://ftp.rfc-editor.org/in-notes/rfc1997.txt)
attribute. 

Usage:

```
java BGPCommunitiesMon.jar [options...] arguments...
 --collectors VAL  : Comma-separated list of BGP Collectors. (default: all)
 --communities VAL : Comma-separated list of 32-bit BGP Community values.
 --outdir VAL      : Path to the output directory. (default: .)
 --period VAL      : Time period for which BGP data will be collected.
                     Format: YYYYMMDD.hhmm,YYYYMMDD.hhmm
 --facilities VAL  : Comma-separated list of facility names to restrict the
                     scope of the analyzed AS links. (default: all)

  Example: java BGPCommunitiesParser.jar --collectors rrc03 --communities 6695:6695,13030:51203 --outdir path/to/dir/ --period 20180407.0000,20180410.0001
```