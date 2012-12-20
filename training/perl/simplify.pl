#!/usr/bin/env perl
use warnings;

my ($input_file, $output_file) = @ARGV;

if (not -f $input_file or not open($in, '<:encoding(UTF-8)', $input_file))
	{
	die "Unable to open file.\n";
	}

my $total = 0;
my $remaining_total = 0;
my $types_pruned = 0;

print "Processing input...\n";
while (my $line = <$in>)
	{
	my ($ngram, @counts) = split /\s+/, $line;
	
	my $count = 0;
	my $users = 0;
	
	for (my $i = 0; $i < @counts; $i++)
		{
		if (($i % 2) == 0)
			{
			$count += $counts[$i];
			}
		else
			{
			$users += $counts[$i];
			}
		}
	
	my $weight = $users;
	
	$total += $weight;
		
	if ($count < 500)
		{
		$types_pruned++;
		next;
		}

	if ($ngram =~ /^(?:[#@]|[^A-Za-z]+$|http.*|www\..*)/)
		{
		$types_pruned++;
		next;
		}
	
	$remaining_total += $weight;
	
	$weights->{$ngram} = $weight;
#	print $out "$ngram\t$count\t$users\n";
	}

close $in;

# secondary pruning stages
printf "First-pass pruned %.1f%% of the total weight (%d types)\n", 100 * (1 - $remaining_total / $total), $types_pruned;


# simplify the ngrams
print "Simplifying case...\n";
my $simple = {};
foreach my $ngram (keys %$weights)
	{
	$simple->{lc $ngram} = $weights->{$ngram};
	}

# reduce it to the top K
my $k = 20000;
print "Pruning to top $k...\n";
my @ngrams = sort { $simple->{$b} <=> $simple->{$a} } keys %$simple;
for (my $i = $k; $i < @ngrams; $i++)
	{
	my $ngram = $ngrams[$i];
	my $weight = $simple->{$ngram};
	$remaining_total -= $weight;
	$types_pruned++;
	
	delete $simple->{$ngram};
	}

printf "After second pass, pruned %.1f%% of the total weight (%d types)\n", 100 * (1 - $remaining_total / $total), $types_pruned;

print "Saving...\n";

if (not open ($out, '>:encoding(UTF-8)', $output_file))
	{
	die "Unable to open output file.\n";
	}

print $out "Total\t$total\n";
print $out "Unpruned\t$remaining_total\n";

printf "Pruned %.1f%% of the total weight (%d types)\n", 100 * (1 - $remaining_total / $total), $types_pruned;

	
@ngrams = sort { $simple->{$b} <=> $simple->{$a} } keys %$simple;
foreach my $ngram (@ngrams)
	{
	print $out "$ngram\t$simple->{$ngram}\n";
	}
close $out;


