
open(my $in, '<:encoding(UTF-8)', $ARGV[0]) or die "$!\n";
while (1)
	{
	my $languageCode = <$in>;
	last if (not $languageCode);
	my $languageName = <$in>;
	$languageName =~ s/[\r\n]//g;
	
	for (my $i = 0; $i < 6; $i++)
		{
		my $junk = <$in>;
		}
		
	push @languages, $languageName;
	}
close $in;

@languages = sort @languages;
print join('\n', @languages) . "\n";
