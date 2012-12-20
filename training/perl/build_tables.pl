
use warnings;

my $dir = $ARGV[0];

opendir DIR, $dir;
@files = readdir DIR;
closedir DIR;

my @zips = grep /_50K\.zip$/, @files;

foreach my $zip (@zips)
	{
	my $decompressed = $zip;
	$decompressed =~ s/\.zip$/.txt/;
	
	if (not -e $decompressed)
		{
		system 'unzip', $zip;
		}
	
	if (not -e $decompressed)
		{
		print "$decompressed doesn't exist even after unzipping...\n";
		die;
		}
	
	open my $in, '<:encoding(UTF-8)', $decompressed;
	my $counts = {};
	while (my $line = <$in>)
		{
		$line =~ s/[\r\n]//g;
		
		my ($word, $count) = split / /, $line;
		$counts->{$word} = $count;
		}
	close $in;
	
	my $char_ngrams = {};
	foreach my $word (keys %$counts)
		{
		# assume space-separated for now
		my @chars = ( ' ' );
		
		for (my $i = 0; $i < length($word); $i++)
			{
			my $char = substr($word, $i, 1);
			
			$char_ngrams->{$char} += $counts->{$word};
			
			$char_ngrams->{$chars[-1] . $char} += $counts->{$word};
			
#			if (@chars > 1)
#				{
#				$char_ngrams->{$chars[-2] . $chars[-1] . $char} += $counts->{$word};
#				}
			
			push @chars, $char;
			}
		
		$char_ngrams->{$chars[-1] . ' '} += $counts->{$word};
#		$char_ngrams->{$chars[-2] . $chars[-1] . ' '} += $counts->{$word};
		}
		
	my $char_table = $zip;
	$char_table =~ s/\.zip$/.chars.txt/;

	open my $out, '>:encoding(UTF-8)', $char_table;
	foreach my $ngram (sort { $char_ngrams->{$b} <=> $char_ngrams->{$a} } keys %$char_ngrams)
		{
		print $out "$ngram $char_ngrams->{$ngram}\n";
		}
	close $out;
	}