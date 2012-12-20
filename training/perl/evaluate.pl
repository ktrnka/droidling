use warnings;

$d = 0.5;

my ($training_dir, $testing_dir) =  @ARGV;

opendir DIR, $training_dir;
@training_files = grep /_50K\.chars\.txt/, readdir DIR;
closedir DIR;

foreach my $file (@training_files)
	{
	if ($file =~ /^(.*)_50K\.chars\.txt$/i)
		{
		$lang = $1;
		}
	else
		{
		print "File doesn't match: $file\n";
		die;
		}
	
	open my $in, '<:encoding(UTF-8)', $file;
	while (my $line = <$in>)
		{
		if ($line =~ /^(.+) (\d+)$/)
			{
			$counts->{$lang}->{$1} = $2;
			}
		else
			{
			print "Umatched line in $file: $line\n";
			die;
			}
		}
	close $in;
	
	my $total = 0;
	foreach my $chars (keys %{$counts->{$lang}})
		{
		next if (length($chars) != 1);
		
		$unigrams->{$lang}->{$chars} = $counts->{$lang}->{$chars};
		$total += $counts->{$lang}->{$chars};
		}
	
	foreach my $char (keys %{$unigrams->{$lang}})
		{
		$unigrams->{$lang}->{$char} = ($unigrams->{$lang}->{$char} - $d) / $total;
		$unigrams->{$lang}->{'UNDEF'} = ($d / $total) / scalar(keys(%{$unigrams->{$lang}}));
		
		$unigrams_any->{$char}++;
		}
	}

opendir DIR, $testing_dir;
@testing_files = grep /\.txt/, readdir DIR;
closedir DIR;

foreach my $file (@testing_files)
	{
	my $encoding = 'ISO-8859-1';
	if ($file =~ /iso-?8859[_\-](\d)/i)
		{
		$encoding = "ISO-8859-$1";
		}
	open my $in, "<:encoding($encoding)", "$testing_dir/$file";
	
	my $scores = {};
	my $num_chars = 0;

	while (my $line = <$in>)
		{
		$line =~ s/[\r\n]+//;
		
		for (my $i = 0; $i < length($line); $i++)
			{
			my $char = substr($line, $i, 1);
			
			next if (not defined $unigrams_any->{$char});
			
			$num_chars++;
			foreach my $lang (keys %$unigrams)
				{
				if (defined $unigrams->{$lang}->{$char})
					{
					$scores->{$lang} += log $unigrams->{$lang}->{$char};
					}
				else
					{
					$scores->{$lang} += log $unigrams->{$lang}->{'UNDEF'};
					}
				}
			}
		}
	close $in;

	my @langs = sort { $scores->{$b} <=> $scores->{$a} } keys %$scores;
	print "Classification for $file ($encoding):\n";
	for (my $i = 0; $i < @langs and $i < 5; $i++)
		{
		printf "\t$langs[$i]: %.2e\n", $scores->{$langs[$i]} / $num_chars;
		}


	}