# BGPCommunityWatch

BGPCommunityWatch can be used for the analysis of historical or real-time BGP data to
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

  Example: java BGPCommunityWatch.jar --collectors rrc00 --communities 2914:1201 --outdir path/to/dir/ --period 20180407.0000,20180410.0001
```

## How to set up

### Required software

BGPCommunityWatch uses BGPReader  v2.0 to obtain a stream of BGP data,
therefore you need to install [BGPStream](https://bgpstream.caida.org/v2-beta).

### Required input datasets

The parsing of the BGP paths requires the following CAIDA datasets as input:
  * [AS relationships](http://www.caida.org/data/as-relationships/). Both serial-1 and serial-2 can be used, but serial-2 is more complete and therefore preferred.
  * [IXPs Dataset](http://www.caida.org/data/ixps/), in particular the `ixs.jsonl` and `ix-asns.jsonl` files.
  
Sample files are included already in the `data/` directory of this repository,
but you should update them depending on the measurement period of your choice.

### Configuration

Once you install BGPstream and download the required datasets, 
edit the `resources/config.properties` file to define the following properties:
- `bgpreader_bin`: Filepath to the BGPReader binary
- `relationships_file`: Filepath to the bzip'd AS relationships file
- `ix_dataset`: Filepath to the `ixs.jsonl` file
- `ix_asn_dataset`: Filepath to the `ix-asns_201802.jsonl`
- `pdb_netfac_url`: The URL to the endpoint of the PeeringDB API that returns the AS-to-Facility memberships.
- `pdb_rsasn_url`: The URL to the endpoint of the PeeringDB API that returns the ASNs of type Route Server.
- `euroix_url`: The URL to the Euro-IX IXP Service Matrix. **Note:** At the moment the Euro-IX service matrix is not publicly available, so this property is more of a future placeholder.
- `stability_hours`: The number of consecutive hours during which BGPCommunityWatch should not observe any change in the path between a given (BGP peer IP, prefix) pair, in order to consider the path 'stable'.


The default values in the `resources/config.properties` should work out-of-the-box,
if you cloned this repository and didn't change the default BGPStream configuration.


