#!/usr/bin/env perl
use warnings;

my ($contraction_reference_file, $contraction_bad_file, $output_file) = @ARGV;

if (not -f $contraction_reference_file or not open($in, '<:encoding(UTF-8)', $contraction_reference_file))
	{
	die;
	}

my $ref_total = <$in>;
chomp $ref_total;

while (my $line = <$in>)
	{
	chomp $line;
	my ($word, $count) = split /\t/, $line;
	$ref_counts->{$word} = $count;
	}

close $in;

# make a list of contractions
my @contractions = sort { $ref_counts->{$b} <=> $ref_counts->{$a} } grep {length($_) <= 6} grep /'/, keys %$ref_counts;
print "Contractions:\n";
foreach my $word (@contractions)
	{
	print "\t$word: $ref_counts->{$word}\n";
	}

# load the file with messed up contractions
if (not -f $contraction_bad_file or not open($in, '<:encoding(UTF-8)', $contraction_bad_file))
	{
	die;
	}

$line = <$in>;
chomp $line;
($name, $total) = split /\t/, $line;

$line = <$in>;
chomp $line;
($name, $unpruned_total) = split /\t/, $line;
while ($line = <$in>)
	{
	chomp $line;
	my ($word, $count) = split /\t/, $line;
	$counts->{$word} = $count;
	}

# iterate and figure out which sub-pieces are unique or not
foreach my $word (@contractions)
	{
	my @parts = split /'/, $word;
	
	foreach my $part (@parts)
		{
		$part_counts->{$part}++;
		}
	}

# display the uniqueness
my @unique = grep { $part_counts->{$_} == 1 && not defined $ref_counts->{$_} } keys %$part_counts;
my @non_unique = grep { $part_counts->{$_} != 1 } keys %$part_counts;

print "Unique parts: " . join(', ', @unique) . "\n";
print "Non-unique parts: " . join(', ', @non_unique) . "\n";

print "Total before approximations: $total\n";

# iterate
foreach my $contraction (@contractions)
	{
	my @parts = split /'/, $contraction;
	
	next if (@parts > 2 or length($contraction) <= 1);
	
	my $reference_prob = $ref_counts->{$contraction} / $ref_total;
	
	my $expected_count = $total * $reference_prob;
	
	if (defined $counts->{$parts[0]} and defined $counts->{$parts[1]})
		{
		print "Found contraction to merge: $contraction, expected frequency " . int($expected_count) . "\n";
		
		if ($part_counts->{$parts[0]} == 1 and not defined $ref_counts->{$parts[0]})
			{
#			print "\t'$parts[0]' is unique, can subtract $counts->{$parts[0]} from both $parts[0]/$counts->{$parts[0]} and $parts[1]/$counts->{$parts[1]}\n";
			}
		elsif ($part_counts->{$parts[1]} == 1 and not defined $ref_counts->{$parts[1]})
			{
#			print "\t'$parts[1]' is unique, can subtract $counts->{$parts[1]} from both $parts[0]/$counts->{$parts[0]} and $parts[1]/$counts->{$parts[1]}\n";
			}
			
#		if (defined $ref_counts->{$parts[0]})
#			{
#			print "\tDON'T KNOW WHAT TO DO WITH A REAL-WORD $parts[0] (freq $counts->{$parts[0]})\n";
#			next;
#			}

#		if (defined $ref_counts->{$parts[1]})
#			{
#			print "\tDON'T KNOW WHAT TO DO WITH A REAL-WORD $parts[1] (freq $counts->{$parts[0]})\n";
#			next;
#			}
		
		# add the contraction with the new frequency
		$counts->{$contraction} = $expected_count;
		$total += $expected_count;
		$unpruned_total += $expected_count;
		
		# remove the pieces
		foreach my $part (@parts)
			{
			# if it's a real word, don't decrease the frequency
			if (defined $ref_counts->{$part})
				{
				print "\tNot subtracting from $part\n";
				next;
				}
			
			# store the original frequency of the part in the messed up unigrams
			if (not defined $orig_counts->{$part})
				{
				$orig_counts->{$part} = $counts->{$part};
				}
			
			print "\tSubtracting " . int($orig_counts->{$part} / $part_counts->{$part}) . " from $part\n";

			$counts->{$part} -= $orig_counts->{$part} / $part_counts->{$part};
			$total -= $orig_counts->{$part} / $part_counts->{$part};
			$unpruned_total -= $orig_counts->{$part} / $part_counts->{$part};
			
			if ($counts->{$part} == 0)
				{
				delete $counts->{$part};
				}
			}
		}
	}

	
print "Total after approximations: $total\n";
printf "Retained %.1f%% of the probability mass.\n", 100 * $unpruned_total / $total;

# insert special cases 'lol'
if (not defined $counts->{'lol'})
	{
	$counts->{'lol'} = $counts->{'u'};
	}
binmode(STDOUT, ":utf8");
foreach my $word (keys %$counts)
	{
	my $copy = $word;
	$copy =~ s/[\N{U+00B4}\N{U+0091}\N{U+0092}\N{U+2019}\N{U+2018}]/'/g;
	$copy =~ s/[\N{U+0093}\N{U+0094}\N{U+201C}\N{U+201D}]/"/g;
	$copy =~ s/\x{0098}/~/g;
	$copy =~ s/\N{U+2013}/-/g;
	$copy =~ s/\N{U+2026}/.../g;
	$copy =~ s/\N{U+2665}//g;
	
	next if ($copy eq $word);
	$copy =~ s/[^\N{U+01}-\N{U+FF}]//g;
	
	$counts->{$copy} += $counts->{$word};
	delete $counts->{$word};
	}

foreach my $word (keys %$counts)
	{
	if ($word =~ m/^&.+;$/)
		{
		delete $counts->{$word};
		}
	}

if (not open($out, '>:encoding(UTF-8)', $output_file))
	{
	die;
	}

print $out int($total) . "\n";
#print $out "Unpruned\t$unpruned_total\n";
my @words = sort { $counts->{$b} <=> $counts->{$a} } keys %$counts;

for (my $i = 0; $i < @words and $i < 6000; $i++)
	{
	my $word = $words[$i];
	
	next if ($counts->{$word} == 0);
	
	print $out "$word\t" . int($counts->{$word}) . "\n";
	}
close $out;