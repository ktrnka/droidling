package LangIDTable;
use warnings;
use Encode qw(encode decode);
use HCParser;

$predictor = 'word_unigram_match';

sub new
	{
	my ($class, $language_name, $iso639_1_code, $code3) = @_;

	my $this = { 'code2' => $iso639_1_code, 'code3' => $code3, 'name' => $language_name, 'total' => 0 };
	
	bless $this;
	return $this;
	}

# train a full character unigram and bigram table
sub train
	{
	my ($this, $unigram_file) = @_;
	
	if ($unigram_file =~ /^(.*)\.zip$/)
		{
		my $target = "$1.txt";
		
		if (not -e $target)
			{
			system 'unzip', $unigram_file;
			
			if (not -e $target)
				{
				die "$target should exist after unzipping $unigram_file\n";
				}
			}
		
		$unigram_file = $target;
		}
	
	open (my $in, '<:encoding(UTF-8)', $unigram_file) or die "Failed to load $unigram_file: $!\n";
	
	while (my $line = <$in>)
		{
		my ($word, $count) = split /\s+/, $line;
		
		$this->{'total_word_unigrams'} += $count;
		$this->{'word_ngrams'}->{$word} = $count;
		
		my @chars = ( ' ' );
		
		for (my $i = 0; $i < length($word); $i++)
			{
			my $char = substr($word, $i, 1);
			
			$this->{'char_ngrams'}->{$char} += $count;
			$this->{'char_ngrams'}->{$chars[-1] . $char} += $count;
						
			push @chars, $char;
			}
		
		$this->{'char_ngrams'}->{$chars[-1] . ' '} += $count;
		}
	
	# compute char unigram and bigram totals
	foreach my $chars (keys %{$this->{'char_ngrams'}})
		{
		$this->{'total_char_ngrams'}->[length $chars] += $this->{'char_ngrams'}->{$chars};
		}
	
	close $in;
	}

# train from a HC-style file
sub train_hc
	{
	my ($this, @hc_files) = @_;
	
	my $parser = new HCParser;
	
	$this->{'total_word_unigrams'} = 0;
	
	foreach my $hc_file (@hc_files)
		{
		$parser->open($hc_file);
		
		my $line_no = 0;
		while (my $values = $parser->read())
			{
			$line_no++;
			next if ($line_no < 10000);	# reserved for testing data
			
			$values->{'text'} = lc $values->{'text'};
			$values->{'text'} =~ s/(?:\p{IsPunct}\s|\s\p{IsPunct})/ /g;
			$values->{'text'} =~ s/(?:\p{IsPunct}$|^\p{IsPunct})//g;
			my @tokens = split /\s+/, $values->{'text'};
			foreach my $word (@tokens)
				{
				$this->{'word_ngrams'}->{$word}++;
				}
			}
		
		$parser->close($hc_file);
		}
	
	# accumulate various stats
	foreach my $word (keys %{$this->{'word_ngrams'}})
		{
		my $count = $this->{'word_ngrams'}->{$word};

		$this->{'total_word_unigrams'} += $count;
		
		my @chars = ( ' ' );
		for (my $i = 0; $i < length($word); $i++)
			{
			my $char = substr($word, $i, 1);
			
			$this->{'char_ngrams'}->{$char} += $count;
			$this->{'char_ngrams'}->{$chars[-1] . $char} += $count;
						
			push @chars, $char;
			}
		$this->{'char_ngrams'}->{$chars[-1] . ' '} += $count;

		}

	# compute char unigram and bigram totals
	foreach my $chars (keys %{$this->{'char_ngrams'}})
		{
		$this->{'total_char_ngrams'}->[length $chars] += $this->{'char_ngrams'}->{$chars};
		}

	}

sub save_word_unigrams
	{
	my ($this, $topN, $outfile) = @_;
	
	my @words = sort { $this->{'word_ngrams'}->{$b} <=> $this->{'word_ngrams'}->{$a} } keys %{$this->{'word_ngrams'}};
	open(my $out, '>:encoding(UTF-8)', $outfile) or die "$!\n";
	print $out "$this->{'total_word_unigrams'}\n";
	for (my $i = 0; $i < @words and $i < $topN; $i++)
		{
		print $out "$words[$i] $this->{'word_ngrams'}->{$words[$i]}\n";
		}
	close $out;
	
	if (@words < $topN)
		{
		warn "$this->{code2} has fewer then $topN words.\n";
		}
	}

# alternative training for languages that aren't space-segmented
sub train_hc_nonspace
	{
	my ($this, @hc_files) = @_;
	
	my $parser = new HCParser;
	
	$this->{'total_word_unigrams'} = 0;
	
	foreach my $hc_file (@hc_files)
		{
		$parser->open($hc_file);
		
		my $line_no = 0;
		while (my $values = $parser->read())
			{
			$line_no++;
			next if ($line_no < 10000);	# reserved for testing data
			
			$values->{'text'} =~ s/(?:\p{IsPunct}+\s|\s\p{IsPunct}+)/ /g;
			$values->{'text'} =~ s/(?:\p{IsPunct}+$|^\p{IsPunct}+)//g;

			for (my $i = 0; $i < length($values->{'text'}); $i++)
				{
				my $char = substr $values->{'text'}, $i, 1;
				$this->{'char_ngrams'}->{$char}++;
				}
			}
		
		$parser->close($hc_file);
		}
	
	# remove the space char; it's not defined in any other language
	if (defined $this->{'char_ngrams'}->{' '})
		{
		delete $this->{'char_ngrams'}->{' '};
		}
	
	# compute char unigram and bigram totals
	foreach my $chars (keys %{$this->{'char_ngrams'}})
		{
		$this->{'total_char_ngrams'}->[length $chars] += $this->{'char_ngrams'}->{$chars};
		}
	}

# prune the model and collect some stats
sub prune
	{
	my ($this, $target_probability_mass, $max_word_unigrams, $max_char_unigrams, $max_char_bigrams) = @_;
	
	print "Pruning to top $max_word_unigrams word unigrams\n";
	my @words = keys %{$this->{'word_ngrams'}};
	@words = sort { $this->{'word_ngrams'}->{$b} <=> $this->{'word_ngrams'}->{$a} } @words;
	
	# find the cutoff point
	my $cutoff = undef;
	my $retained_count = 0;
	for (my $i = 0; $i < @words; $i++)
		{
		$retained_count += $this->{'word_ngrams'}->{$words[$i]};
		
		if ($retained_count / $this->{'total_word_unigrams'} >= $target_probability_mass)
			{
			$cutoff = $i + 1;
			last;
			}
		}
	if (not defined $cutoff)
		{
		$cutoff = 0;
		}

	printf "\tto retain %.1f%% of the probability, need to save $cutoff words\n", $target_probability_mass * 100;

	if ($cutoff < $max_word_unigrams)
		{
		$max_word_unigrams = $cutoff;
		}
	else
		{
		print "\tbut that's over the min $max_word_unigrams, saving that instead\n";
		}
	
	# prune
	for (my $i = $max_word_unigrams; $i < @words; $i++)
		{
		delete $this->{'word_ngrams'}->{$words[$i]};
		}

	# double-checking
	$retained_count = 0;
	for (my $i = 0; $i < $max_word_unigrams and $i < @words; $i++)
		{
		$retained_count += $this->{'word_ngrams'}->{$words[$i]};
		}
		
	printf "\tretained %.1f%% of the frequency\n", 100 * $retained_count / (1 + $this->{'total_word_unigrams'});
	$this->{'word_ngrams_retained'} = $retained_count / (1 + $this->{'total_word_unigrams'});

	
	print "Pruning to top $max_char_unigrams char unigrams\n";
	my @chars = grep { length($_) == 1 } keys %{$this->{'char_ngrams'}};
	@chars = sort { $this->{'char_ngrams'}->{$b} <=> $this->{'char_ngrams'}->{$a} } @chars;
	
	# find the cutoff
	$cutoff = undef;
	$retained_count = 0;
	for (my $i = 0; $i < @chars; $i++)
		{
		$retained_count += $this->{'char_ngrams'}->{$chars[$i]};
		
		if ($retained_count / $this->{'total_char_ngrams'}->[1] >= $target_probability_mass)
			{
			$cutoff = $i + 1;
			last;
			}
		}
	printf "\tto retain %.1f%% of the probability, need to save $cutoff chars\n", $target_probability_mass * 100;
	if (not defined $cutoff)
		{
		$cutoff = 0;
		}

	if ($cutoff < $max_char_unigrams)
		{
		$max_char_unigrams = $cutoff;
		}
	else
		{
		print "\tbut that's over the min $max_char_unigrams, saving that instead\n";
		}
	
	# prune
	for (my $i = $max_char_unigrams; $i < @chars; $i++)
		{
		delete $this->{'char_ngrams'}->{$chars[$i]};
		}

	# double-checking
	$retained_count = 0;
	for (my $i = 0; $i < $max_char_unigrams and $i < @chars; $i++)
		{
		$retained_count += $this->{'char_ngrams'}->{$chars[$i]};
		}
	printf "\tretained %.1f%% of the frequency\n", 100 * $retained_count / (1 + $this->{'total_char_ngrams'}->[1]);
	$this->{'char_ngrams_retained'}->[1] = $retained_count / (1 + $this->{'total_char_ngrams'}->[1]);

	
	print "Pruning to top $max_char_bigrams char bigrams\n";
	my @pairs = grep { length($_) == 2 } keys %{$this->{'char_ngrams'}};
	@pairs = sort { $this->{'char_ngrams'}->{$b} <=> $this->{'char_ngrams'}->{$a} } @pairs;
	
	# find the cutoff
	$cutoff = undef;
	$retained_count = 0;
	for (my $i = 0; $i < @pairs; $i++)
		{
		$retained_count += $this->{'char_ngrams'}->{$pairs[$i]};
		
		if ($retained_count / $this->{'total_char_ngrams'}->[2] >= $target_probability_mass)
			{
			$cutoff = $i + 1;
			last;
			}
		}
	if (not defined $cutoff)
		{
		$cutoff = 0;
		}
	printf "\tto retain %.1f%% of the probability, need to save $cutoff char bigrams\n", $target_probability_mass * 100;

	if ($cutoff < $max_char_bigrams)
		{
		$max_char_bigrams = $cutoff;
		}
	else
		{
		print "\tbut that's over the min $max_char_bigrams, saving that instead\n";
		}
	
	# prune
	for (my $i = $max_char_bigrams; $i < @pairs; $i++)
		{
		delete $this->{'char_ngrams'}->{$pairs[$i]};
		}
	
	# compute retained
	$retained_count = 0;
	for (my $i = 0; $i < $max_char_bigrams and $i < @pairs; $i++)
		{
		$retained_count += $this->{'char_ngrams'}->{$pairs[$i]};
		}
	printf "\tretained %.1f%% of the frequency\n", 100 * $retained_count / (1 + $this->{'total_char_ngrams'}->[2]);
	$this->{'char_ngrams_retained'}->[2] = $retained_count / (1 + $this->{'total_char_ngrams'}->[2]);
	}

sub save
	{
	my ($this, $dir) = @_;
	
	if (not -e $dir)
		{
		mkdir $dir;
		}

	open (my $out, '>:encoding(UTF-8)', "$dir/$this->{code2}.lid") or die "Failed to save: $!\n";
	print $out "$this->{code2}\n";
	print $out "$this->{name}\n";
	print $out join(' ', sort { $this->{'word_ngrams'}->{$b} <=> $this->{'word_ngrams'}->{$a} } keys %{$this->{'word_ngrams'}}) . "\n";
	print $out join(' ', sort { $this->{'char_ngrams'}->{$b} <=> $this->{'char_ngrams'}->{$a} } grep { length($_) == 1 } keys %{$this->{'char_ngrams'}}) . "\n";
	print $out join('|', sort { $this->{'char_ngrams'}->{$b} <=> $this->{'char_ngrams'}->{$a} } grep { length($_) == 2 } keys %{$this->{'char_ngrams'}}) . "\n";
	
	if (defined $this->{'word_ngrams_diff'})
		{
		print $out join(' ', sort { $this->{'word_ngrams_diff'}->{$b} <=> $this->{'word_ngrams_diff'}->{$a} } keys %{$this->{'word_ngrams_diff'}}) . "\n";
		}
	if (defined $this->{'char_ngrams_diff'})
		{
		print $out join(' ', sort { $this->{'char_ngrams_diff'}->{$b} <=> $this->{'char_ngrams_diff'}->{$a} } grep { length($_) == 1 } keys %{$this->{'char_ngrams_diff'}}) . "\n";
		print $out join('|', sort { $this->{'char_ngrams_diff'}->{$b} <=> $this->{'char_ngrams_diff'}->{$a} } grep { length($_) == 2 } keys %{$this->{'char_ngrams_diff'}}) . "\n";
		}
	close $out;
	}

sub differentiate
	{
	my ($this, @others) = @_;
	
	# build an aggregate char unigram model
	my $agg = new LangIDTable('aggregated', 'aggregated');
	foreach my $model (@others)
		{
		foreach my $word (keys %{$model->{'word_ngrams'}})
			{
			$agg->{'word_ngrams'}->{$word} += $model->{'word_ngrams'}->{$word};
			$agg->{'total_word_unigrams'} += $model->{'word_ngrams'}->{$word};
			}
		
		foreach my $chars (keys %{$model->{'char_ngrams'}})
			{
			$agg->{'char_ngrams'}->{$chars} += $model->{'char_ngrams'};
			$agg->{'total_char_ngrams'}->[length $chars] += $model->{'char_ngrams'};
			}
		}
	
	# score all the words
	my $word_scores = {};
	foreach my $word (grep { $this->{'word_ngrams'}->{$_} / $this->{'total_word_unigrams'} > 0.005 } keys %{$this->{'word_ngrams'}})
		{
		$agg->{'word_ngrams'}->{$word} = 0 if (not defined $agg->{'word_ngrams'}->{$word});
		$word_scores->{$word} = $this->{'word_ngrams'}->{$word} * ($this->{'word_ngrams'}->{$word} / $this->{'total_word_unigrams'}) / ( (1 + $agg->{'word_ngrams'}->{$word}) / $agg->{'total_word_unigrams'});
		}
	
	my @best_words = sort { $word_scores->{$b} <=> $word_scores->{$a} } keys %$word_scores;
#	print "Most uniquely $this->{name} words:\n";
	for (my $i = 0; $i < @best_words and $i < 20; $i++)
		{
		my $word = $best_words[$i];
#		print "\t" . encode('ISO8859-1', $word) . ": $word_scores->{$word} = $this->{'word_ngrams'}->{$word} * ($this->{'word_ngrams'}->{$word} / $this->{'total_word_unigrams'}) / ( (1 + $agg->{'word_ngrams'}->{$word}) / $agg->{'total_word_unigrams'})\n";
		
		$this->{'word_ngrams_diff'}->{$word} = $word_scores->{$word};
		}
	
	# score all the ngrams
	my $chars_scores = {};
	foreach my $chars (grep { $this->{'char_ngrams'}->{$_} / $this->{'total_char_ngrams'}->[length $_] > 0.001 } keys %{$this->{'char_ngrams'}})
		{
		$agg->{'char_ngrams'}->{$chars} = 0 if (not defined $agg->{'char_ngrams'}->{$chars});
		$chars_scores->{$chars} = $this->{'char_ngrams'}->{$chars} * ($this->{'char_ngrams'}->{$chars} / $this->{'total_char_ngrams'}->[length $chars]) / ( (1 + $agg->{'char_ngrams'}->{$chars}) / $agg->{'total_char_ngrams'}->[length $chars]);
		}

	my @best_chars = sort { $chars_scores->{$b} <=> $chars_scores->{$a} } grep { length($_) == 1 } keys %$chars_scores;
#	print "Most uniquely $this->{name} chars:\n";
	for (my $i = 0; $i < @best_chars and $i < 20; $i++)
		{
		my $chars = $best_chars[$i];
#		print "\t" . encode('ISO8859-1', $chars) . ": $chars_scores->{$chars} = $this->{'char_ngrams'}->{$chars} * ($this->{'char_ngrams'}->{$chars} / $this->{'total_char_ngrams'}->[length $chars]) / ( (1 + $agg->{'char_ngrams'}->{$chars}) / $agg->{'total_char_ngrams'}->[length $chars])\n";
		
		$this->{'char_ngrams_diff'}->{$chars} = $chars_scores->{$chars};
		}

	@best_chars = sort { $chars_scores->{$b} <=> $chars_scores->{$a} } grep { length($_) == 2 } keys %$chars_scores;
#	print "Most uniquely $this->{name} char pairs:\n";
	for (my $i = 0; $i < @best_chars and $i < 20; $i++)
		{
		my $chars = $best_chars[$i];
#		print "\t" . encode('ISO8859-1', $chars) . ": $chars_scores->{$chars} = $this->{'char_ngrams'}->{$chars} * ($this->{'char_ngrams'}->{$chars} / $this->{'total_char_ngrams'}->[length $chars]) / ( (1 + $agg->{'char_ngrams'}->{$chars}) / $agg->{'total_char_ngrams'}->[length $chars])\n";
		
		$this->{'char_ngrams_diff'}->{$chars} = $chars_scores->{$chars};
		}
	}

sub load
	{
	my ($this, $file) = @_;
	
	open(my $in, '<:encoding(UTF-8)', $file) or die "Failed to load $file: $!\n";
	
	$this->{'code2'} = <$in>;
	$this->{'code2'} =~ s/[\r\n]+//;
	
	$this->{'name'} = <$in>;
	$this->{'name'} =~ s/[\r\n]+//;;
	
	my $line = <$in>;
	$line =~ s/[\r\n]+//;;
	
	my $current_score = 0.5;
	foreach my $word (split / /, $line)
		{
		$this->{'word_ngrams'}->{$word} = $current_score;
		$this->{'total_word_unigrams'} += $current_score;
		$current_score /= 2;
		}
	
	$line = <$in>;
	$line =~ s/[\r\n]+//;;
	$current_score = 0.5;
	foreach my $char (split / /, $line)
		{
		$this->{'char_ngrams'}->{$char} = $current_score;
		$this->{'total_char_ngrams'}->[1] += $current_score;
		$current_score /= 2;
		}
	
	$line = <$in>;
	$line =~ s/[\r\n]+//;;
	$current_score = 0.5;
	foreach my $chars (split /\|/, $line)
		{
		$this->{'char_ngrams'}->{$chars} = $current_score;
		$this->{'total_char_ngrams'}->[2] += $current_score;
		$current_score /= 2;
		}
		
	$line = <$in>;
	$line =~ s/[\r\n]+//;;
	$current_score = 0.5;
	foreach my $word (split / /, $line)
		{
		$this->{'word_ngrams_diff'}->{$word} = $current_score;
		$current_score /= 2;
		}

	$line = <$in>;
	$line =~ s/[\r\n]+//;;
	$current_score = 0.5;
	foreach my $char (split / /, $line)
		{
		$this->{'char_ngrams_diff'}->{$char} = $current_score;
		$current_score /= 2;
		}


	$line = <$in>;
	$line =~ s/[\r\n]+//;;
	$current_score = 0.5;
	foreach my $chars (split /\|/, $line)
		{
		#print "Found '$chars'\n";
		$this->{'char_ngrams_diff'}->{$chars} = $current_score;
		$current_score /= 2;
		}

	close $in;
	}

# score of the text being of this language (higher is a better match)
sub score
	{
	my ($this, $text) = @_;
	
	my @words = split /\s+/, $text;
	my $stats = {
		'word_unigram_match' => 0,
		'word_unigram_diff_match' => 0,
		'char_unigram_match' => 0,
		'char_unigram_diff_match' => 0,
		'char_bigram_match' => 0,
		'char_bigram_diff_match' => 0
		};
	
	foreach my $word (@words)
		{
		# source data seems to be entirely lowercase
		$word = lc $word;
		
		# normal word score
		$stats->{'words'}++;
		if (defined $this->{'word_ngrams'}->{$word})
			{
			$stats->{'word_unigram_match'}++;
			}
		if (defined $this->{'word_ngrams_diff'}->{$word})
			{
			$stats->{'word_unigram_diff_match'}++;
			}
		
		my @history = ( ' ' );
		for (my $i = 0; $i < length($word); $i++)
			{
			my $char = substr $word, $i, 1;
			
			# char unigrams
			$stats->{'chars'}++;
			if (defined $this->{'char_ngrams'}->{$char})
				{
				$stats->{'char_unigram_match'}++;
				}
			if (defined $this->{'char_ngrams_diff'}->{$char})
				{
				$stats->{'char_unigram_diff_match'}++;
				}
			
			# char bigrams
			my $char_pair = $history[-1] . $char;
			$stats->{'char_pairs'}++;
			if (defined $this->{'char_ngrams'}->{$char_pair})
				{
				$stats->{'char_bigram_match'}++;
				}
			if (defined $this->{'char_ngrams_diff'}->{$char_pair})
				{
				$stats->{'char_bigram_diff_match'}++;
				}

			push @history, $char;
			}

		# char bigrams
		my $char_pair = $history[-1] . ' ';
		$stats->{'char_pairs'}++;
		if (defined $this->{'char_ngrams'}->{$char_pair})
			{
			$stats->{'char_bigram_match'}++;
			}
		if (defined $this->{'char_ngrams_diff'}->{$char_pair})
			{
			$stats->{'char_bigram_diff_match'}++;
			}
		}
	
	$stats->{'word_unigram_match'} /= $stats->{'words'};
	$stats->{'word_unigram_diff_match'} /= $stats->{'words'};
	
	$stats->{'char_unigram_match'} /= $stats->{'chars'};
	$stats->{'char_unigram_diff_match'} /= $stats->{'chars'};
	
	$stats->{'char_bigram_match'} /= $stats->{'char_pairs'}++;
	$stats->{'char_bigram_diff_match'} /= $stats->{'char_pairs'}++;
	
	my $message = <<HERE;
	Matching factors for $this->{name} ($this->{code2}):
		Word unigrams: $stats->{'word_unigram_match'}
		Word unigrams (discrim): $stats->{'word_unigram_diff_match'}
		
		Char unigrams: $stats->{'char_unigram_match'}
		Char unigrams (discrim): $stats->{'char_unigram_diff_match'}
		
		Char bigrams: $stats->{'char_bigram_match'}
		Char bigrams (discrim): $stats->{'char_bigram_diff_match'}
HERE

	if (defined $stats->{$predictor})
		{
		return $stats->{$predictor};
		}
	elsif ($predictor eq 'all')
		{
		return $stats->{'word_unigram_match'} + $stats->{'word_unigram_diff_match'} + $stats->{'char_unigram_match'} + $stats->{'char_unigram_diff_match'} + $stats->{'char_bigram_match'};
		}
	elsif ($predictor eq 'no-words')
		{
		return $stats->{'char_unigram_match'} + $stats->{'char_unigram_diff_match'} + $stats->{'char_bigram_match'};
		}
	else
		{
		die "Unsupported predictor value: $predictor\n";
		}
	}

sub get_max_score
	{
	if ($predictor eq 'all')
		{
		return 5;
		}
	elsif ($predictor eq 'no-words')
		{
		return 3;
		}
	else
		{
		return 1;
		}
	}

sub explain_classification_words
	{
	my ($this, $text, $other_model) = @_;
	
	my $example_words = {};

	# words that make it look different!
	my @words = split /\s+/, $text;
	
	foreach my $word (@words)
		{
		# source data seems to be entirely lowercase
		$word = lc $word;
		
		if (defined $this->{'word_ngrams'}->{$word} and not defined $other_model->{'word_ngrams'}->{$word} and not defined $other_model->{'word_ngrams_diff'}->{$word})
			{
			$example_words->{$word} = $this->{'word_ngrams'}->{$word};
			}
		if (defined $this->{'word_ngrams_diff'}->{$word} and not defined $other_model->{'word_ngrams'}->{$word} and not defined $other_model->{'word_ngrams_diff'}->{$word})
			{
			$example_words->{$word} += $this->{'word_ngrams'}->{$word};
			}
		}
	
	return map { encode('ISO8859-1', $_) } sort { $example_words->{$b} <=> $example_words->{$a} } keys %$example_words;
	}

sub explain_classification_chars
	{
	my ($this, $text, $other_model) = @_;
	
	my $example_chars = {};

	# words that make it look different!
	my @words = split /\s+/, $text;
	
	foreach my $word (@words)
		{
		# source data seems to be entirely lowercase
		$word = lc $word;
		
		my @history = ( ' ' );
		for (my $i = 0; $i < length($word); $i++)
			{
			my $char = substr $word, $i, 1;
			
			# char unigrams
			if (defined $this->{'char_ngrams'}->{$char} and not defined $other_model->{'char_ngrams'}->{$char} and not defined $other_model->{'char_ngrams_diff'}->{$char})
				{
				$example_chars->{$char} = $this->{'char_ngrams'}->{$char};
				}
			if (defined $this->{'char_ngrams_diff'}->{$char} and not defined $other_model->{'char_ngrams'}->{$char} and not defined $other_model->{'char_ngrams_diff'}->{$char})
				{
				$example_chars->{$char} += $this->{'char_ngrams_diff'}->{$char};
				}
			
			# char bigrams
			my $char_pair = $history[-1] . $char;
			if (defined $this->{'char_ngrams'}->{$char_pair} and not defined $other_model->{'char_ngrams'}->{$char_pair} and not defined $other_model->{'char_ngrams_diff'}->{$char_pair})
				{
				$example_chars->{$char_pair} = $this->{'char_ngrams'}->{$char_pair};
				}
			if (defined $this->{'char_ngrams_diff'}->{$char_pair} and not defined $other_model->{'char_ngrams'}->{$char_pair} and not defined $other_model->{'char_ngrams_diff'}->{$char_pair})
				{
				$example_chars->{$char_pair} += $this->{'char_ngrams_diff'}->{$char_pair};
				}

			push @history, $char;
			}

		# char bigrams
		my $char_pair = $history[-1] . ' ';
		if (defined $this->{'char_ngrams'}->{$char_pair} and not defined $other_model->{'char_ngrams'}->{$char_pair} and not defined $other_model->{'char_ngrams_diff'}->{$char_pair})
			{
			$example_chars->{$char_pair} = $this->{'char_ngrams'}->{$char_pair};
			}
		if (defined $this->{'char_ngrams_diff'}->{$char_pair} and not defined $other_model->{'char_ngrams'}->{$char_pair} and not defined $other_model->{'char_ngrams_diff'}->{$char_pair})
			{
			$example_chars->{$char_pair} += $this->{'char_ngrams_diff'}->{$char_pair};
			}
		}
	
	return map { encode('ISO8859-1', $_) } sort { $example_chars->{$b} <=> $example_chars->{$a} } keys %$example_chars;
	}
	
1;
